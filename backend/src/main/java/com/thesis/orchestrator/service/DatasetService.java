package com.thesis.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.orchestrator.domain.Dataset;
import com.thesis.orchestrator.domain.DatasetOrigin;
import com.thesis.orchestrator.domain.Measurement;
import com.thesis.orchestrator.domain.Station;
import com.thesis.orchestrator.dto.DatasetAnalysisResponse;
import com.thesis.orchestrator.dto.DatasetDownload;
import com.thesis.orchestrator.dto.DatasetProvenance;
import com.thesis.orchestrator.dto.DatasetRequest;
import com.thesis.orchestrator.dto.DatasetResponse;
import com.thesis.orchestrator.dto.DatasetStatsResponse;
import com.thesis.orchestrator.dto.DatasetUpdateRequest;
import com.thesis.orchestrator.dto.MeasurementResponse;
import com.thesis.orchestrator.dto.StationExtractionResponse;
import com.thesis.orchestrator.dto.StationResponse;
import com.thesis.orchestrator.exception.DatasetAnalysisException;
import com.thesis.orchestrator.exception.InvalidUploadException;
import com.thesis.orchestrator.exception.NotFoundException;
import com.thesis.orchestrator.integration.DatasetAnalysisClient;
import com.thesis.orchestrator.repository.DatasetRepository;
import com.thesis.orchestrator.repository.MeasurementRepository;
import com.thesis.orchestrator.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);

    private final DatasetRepository datasetRepository;
    private final StationRepository stationRepository;
    private final MeasurementRepository measurementRepository;
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
     * same analyze + persist step as a fetched snapshot. Malformed content (the analysis
     * service returns 4xx -> InvalidDataException) fails the upload and stores
     * nothing; a transient analysis-service outage is tolerated (the dataset is still
     * stored, with null analysis).
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

        // Analyze before persisting so malformed content (InvalidDataException) aborts
        // the upload without leaving an orphaned dataset behind.
        try {
            String filename = name + extensionFor(type);
            DatasetAnalysisResponse analysis = datasetAnalysisClient.analyze(filename, content);
            dataset.setDatasetType(analysis.datasetType());
            dataset.setAnalysisResult(objectMapper.writeValueAsString(analysis));
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize analysis for uploaded dataset {}: {}", name, ex.getMessage());
        } catch (DatasetAnalysisException ex) {
            log.warn("Analysis service unavailable for uploaded dataset {}: {}", name, ex.getMessage());
        }

        return DatasetResponse.from(datasetRepository.save(dataset));
    }

    /**
     * Creates a dataset from one or more uploaded station CSV files plus a column-role
     * mapping. The Python analysis service parses the files into a relational shape
     * (deduplicated stations + long-format measurements); this method persists that shape
     * across the {@code datasets}, {@code stations} and {@code measurements} tables. The
     * dataset row itself carries no {@code content} (the data lives in the relational tables).
     * Runs in a single transaction so a persistence failure rolls everything back; malformed
     * input (analysis service 4xx -&gt; InvalidDataException) aborts before anything is saved.
     */
    @Transactional
    public DatasetResponse uploadStations(
            List<MultipartFile> files, String name, String description, String mappingJson) {
        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new InvalidUploadException("At least one file is required.");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidUploadException("A dataset name is required.");
        }
        if (mappingJson == null || mappingJson.isBlank()) {
            throw new InvalidUploadException("A column-role mapping is required.");
        }

        // Parse + validate in the Python service before persisting anything.
        StationExtractionResponse extraction = datasetAnalysisClient.extractStations(files, mappingJson);

        Dataset dataset = datasetRepository.save(Dataset.builder()
                .name(name)
                .type("CSV")
                .description(description)
                .datasetOrigin(DatasetOrigin.UPLOAD)
                .provenance(serializeProvenance(new DatasetProvenance.UploadProvenance()))
                .createdAt(Instant.now())
                .build());

        UUID datasetId = dataset.getId();

        List<Station> stations = extraction.stations().stream()
                .map(record -> Station.builder()
                        .datasetId(datasetId)
                        .stationExternalId(record.stationExternalId())
                        .latitude(record.latitude())
                        .longitude(record.longitude())
                        .attributes(serializeAttributes(record.attributes()))
                        .build())
                .toList();
        stationRepository.saveAll(stations);

        List<Measurement> measurements = extraction.measurements().stream()
                .map(record -> Measurement.builder()
                        .datasetId(datasetId)
                        .stationExternalId(record.stationExternalId())
                        .timestamp(parseTimestamp(record.timestamp()))
                        .measurementType(record.measurementType())
                        .value(record.value())
                        .valueNumeric(record.valueNumeric())
                        .build())
                .toList();
        measurementRepository.saveAll(measurements);

        return DatasetResponse.from(dataset);
    }

    /** Returns the stations extracted from a station-based upload. */
    public List<StationResponse> getStations(UUID datasetId) {
        findEntity(datasetId);
        return stationRepository.findByDatasetId(datasetId).stream()
                .map(StationResponse::from)
                .toList();
    }

    /** Returns a dataset's measurements (long format), capped at {@code limit} rows. */
    public List<MeasurementResponse> getMeasurements(UUID datasetId, int limit) {
        findEntity(datasetId);
        Pageable pageable = PageRequest.of(0, limit);
        return measurementRepository.findByDatasetId(datasetId, pageable).stream()
                .map(MeasurementResponse::from)
                .toList();
    }

    /** Returns aggregate counts (stations, measurements) for a dataset. */
    public DatasetStatsResponse getStats(UUID datasetId) {
        findEntity(datasetId);
        return new DatasetStatsResponse(
                stationRepository.countByDatasetId(datasetId),
                measurementRepository.countByDatasetId(datasetId)
        );
    }

    public List<DatasetResponse> getAll() {
        return datasetRepository.findAll().stream()
                .map(DatasetResponse::from)
                .toList();
    }

    public DatasetResponse getById(UUID id) {
        return DatasetResponse.from(findEntity(id));
    }

    /** Updates a dataset's editable metadata (name, description). Content and provenance are immutable. */
    public DatasetResponse update(UUID id, DatasetUpdateRequest request) {
        Dataset dataset = findEntity(id);
        dataset.setName(request.name());
        dataset.setDescription(request.description());
        return DatasetResponse.from(datasetRepository.save(dataset));
    }

    @Transactional
    public void delete(UUID id) {
        Dataset dataset = findEntity(id);
        // Stations/measurements reference the dataset by a bare datasetId (no JPA
        // relationship), so cascade-delete their rows explicitly.
        stationRepository.deleteByDatasetId(id);
        measurementRepository.deleteByDatasetId(id);
        datasetRepository.delete(dataset);
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

    /**
     * Returns a dataset's raw content packaged for download: the content plus the
     * filename ("&lt;name&gt;.&lt;ext&gt;") and content type derived from its type.
     */
    public DatasetDownload download(UUID id) {
        Dataset dataset = findEntity(id);
        String content = dataset.getContent();
        if (content == null || content.isBlank()) {
            throw new NotFoundException("Dataset has no downloadable content: " + id);
        }
        String filename = dataset.getName() + extensionFor(dataset.getType());
        String contentType = "CSV".equalsIgnoreCase(dataset.getType()) ? "text/csv" : "application/geo+json";
        return new DatasetDownload(content, filename, contentType);
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

    /** Serializes a station's extra attributes to a JSON object string; null when empty. */
    private String serializeAttributes(java.util.Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    /** Parses an ISO-8601 timestamp emitted by the analysis service; null if absent/unparseable. */
    private Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ex) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored) {
                log.warn("Could not parse measurement timestamp '{}'", value);
                return null;
            }
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
