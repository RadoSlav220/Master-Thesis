package com.thesis.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.orchestrator.domain.Dataset;
import com.thesis.orchestrator.domain.DatasetOrigin;
import com.thesis.orchestrator.dto.DatasetAnalysisResponse;
import com.thesis.orchestrator.dto.DatasetProvenance;
import com.thesis.orchestrator.dto.DatasetRequest;
import com.thesis.orchestrator.dto.DatasetResponse;
import com.thesis.orchestrator.exception.InvalidUploadException;
import com.thesis.orchestrator.exception.NotFoundException;
import com.thesis.orchestrator.integration.DatasetAnalysisClient;
import com.thesis.orchestrator.repository.DatasetRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);

    private final DatasetRepository datasetRepository;
    private final ObjectMapper objectMapper;
    private final DatasetAnalysisClient datasetAnalysisClient;

    public DatasetResponse create(DatasetRequest request) {
        Dataset dataset = Dataset.builder()
                .name(request.name())
                .type(request.type())
                .description(request.description())
                .datasetOrigin(DatasetOrigin.UPLOAD)
                .provenance(serializeProvenance(new DatasetProvenance.UploadProvenance()))
                .createdAt(Instant.now())
                .build();
        return DatasetResponse.from(datasetRepository.save(dataset));
    }

    /**
     * Creates a dataset from an uploaded file (CSV or GeoJSON). The format is derived
     * from the filename extension. GeoJSON gets a fast structural pre-check (must be a
     * FeatureCollection); the raw content is stored verbatim. The upload then runs the
     * same analyze + persist step as a fetched snapshot — analysis failure does not fail
     * the upload, the dataset is still stored (with null analysis).
     */
    public DatasetResponse upload(MultipartFile file, String name, String description) {
        String content = readFile(file);
        String type = detectType(file.getOriginalFilename());
        if ("GEOJSON".equals(type)) {
            validateFeatureCollection(content);
        }

        Dataset dataset = Dataset.builder()
                .name(name)
                .type(type)
                .description(description)
                .content(content)
                .datasetOrigin(DatasetOrigin.UPLOAD)
                .provenance(serializeProvenance(new DatasetProvenance.UploadProvenance()))
                .createdAt(Instant.now())
                .build();
        datasetRepository.save(dataset);

        try {
            String filename = name + extensionFor(type);
            DatasetAnalysisResponse analysis = datasetAnalysisClient.analyze(filename, content);
            dataset.setDatasetType(analysis.datasetType());
            dataset.setAnalysisResult(objectMapper.writeValueAsString(analysis));
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize analysis for uploaded dataset {}: {}", dataset.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("Analysis failed for uploaded dataset {}: {}", dataset.getId(), ex.getMessage());
        }

        return DatasetResponse.from(datasetRepository.save(dataset));
    }

    public List<DatasetResponse> getAll() {
        return datasetRepository.findAll().stream()
                .map(DatasetResponse::from)
                .toList();
    }

    public DatasetResponse getById(UUID id) {
        return DatasetResponse.from(findEntity(id));
    }

    /** Returns the raw GeoJSON FeatureCollection stored for a dataset. */
    public String getGeoJson(UUID id) {
        Dataset dataset = findEntity(id);
        String content = dataset.getContent();
        if (content == null || content.isBlank() || !"GEOJSON".equalsIgnoreCase(dataset.getType())) {
            throw new NotFoundException("Dataset has no GeoJSON content: " + id);
        }
        return content;
    }

    /**
     * Sends the dataset's stored file content to the Python analysis service and
     * returns its structural analysis. Works for any dataset with stored content
     * (CSV or GeoJSON); the filename extension drives the service's format dispatch.
     */
    public DatasetAnalysisResponse analyze(UUID id) {
        Dataset dataset = findEntity(id);
        String content = dataset.getContent();
        if (content == null || content.isBlank()) {
            throw new InvalidUploadException("Dataset has no analyzable file content: " + id);
        }
        String filename = dataset.getName() + extensionFor(dataset.getType());
        return datasetAnalysisClient.analyze(filename, content);
    }

    /** Maps a dataset type to a filename extension the analysis service recognizes. */
    static String extensionFor(String type) {
        return "CSV".equalsIgnoreCase(type) ? ".csv" : ".geojson";
    }

    /** Derives the dataset type from an uploaded filename's extension. */
    private String detectType(String filename) {
        String name = filename == null ? "" : filename.toLowerCase();
        if (name.endsWith(".csv")) {
            return "CSV";
        }
        if (name.endsWith(".geojson") || name.endsWith(".json")) {
            return "GEOJSON";
        }
        throw new InvalidUploadException(
                "Unsupported file type: " + filename + ". Expected .csv, .geojson, or .json.");
    }

    private String serializeProvenance(DatasetProvenance provenance) {
        try {
            return objectMapper.writeValueAsString(provenance);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private Dataset findEntity(UUID id) {
        return datasetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dataset not found: " + id));
    }

    private String readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("Uploaded file is empty.");
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new InvalidUploadException("Could not read uploaded file: " + ex.getMessage());
        }
    }

    private void validateFeatureCollection(String content) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (IOException ex) {
            throw new InvalidUploadException("File is not valid JSON: " + ex.getMessage());
        }
        JsonNode type = root.get("type");
        if (type == null || !"FeatureCollection".equals(type.asText())) {
            throw new InvalidUploadException("GeoJSON must have type \"FeatureCollection\".");
        }
        if (!root.has("features") || !root.get("features").isArray()) {
            throw new InvalidUploadException("GeoJSON FeatureCollection must contain a \"features\" array.");
        }
    }
}
