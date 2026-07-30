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
import com.thesis.orchestrator.exception.InvalidGeoJsonException;
import com.thesis.orchestrator.exception.NotFoundException;
import com.thesis.orchestrator.integration.DatasetAnalysisClient;
import com.thesis.orchestrator.repository.DatasetRepository;
import lombok.RequiredArgsConstructor;
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
     * Creates a dataset from an uploaded GeoJSON file. Performs basic structural
     * validation (must be a FeatureCollection with a features array) and stores the
     * raw content verbatim. No advanced/GIS validation is performed.
     */
    public DatasetResponse upload(MultipartFile file, String name, String description) {
        String content = readFile(file);
        validateFeatureCollection(content);

        Dataset dataset = Dataset.builder()
                .name(name)
                .type("GEOJSON")
                .description(description)
                .content(content)
                .datasetOrigin(DatasetOrigin.UPLOAD)
                .provenance(serializeProvenance(new DatasetProvenance.UploadProvenance()))
                .createdAt(Instant.now())
                .build();
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
            throw new InvalidGeoJsonException("Dataset has no analyzable file content: " + id);
        }
        String filename = dataset.getName() + extensionFor(dataset.getType());
        return datasetAnalysisClient.analyze(filename, content);
    }

    /** Maps a dataset type to a filename extension the analysis service recognizes. */
    static String extensionFor(String type) {
        return "CSV".equalsIgnoreCase(type) ? ".csv" : ".geojson";
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
            throw new InvalidGeoJsonException("Uploaded file is empty.");
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new InvalidGeoJsonException("Could not read uploaded file: " + ex.getMessage());
        }
    }

    private void validateFeatureCollection(String content) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (IOException ex) {
            throw new InvalidGeoJsonException("File is not valid JSON: " + ex.getMessage());
        }
        JsonNode type = root.get("type");
        if (type == null || !"FeatureCollection".equals(type.asText())) {
            throw new InvalidGeoJsonException("GeoJSON must have type \"FeatureCollection\".");
        }
        if (!root.has("features") || !root.get("features").isArray()) {
            throw new InvalidGeoJsonException("GeoJSON FeatureCollection must contain a \"features\" array.");
        }
    }
}
