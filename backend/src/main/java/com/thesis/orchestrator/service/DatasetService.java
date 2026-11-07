package com.thesis.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.orchestrator.domain.Dataset;
import com.thesis.orchestrator.domain.DatasetOrigin;
import com.thesis.orchestrator.dto.DatasetDownload;
import com.thesis.orchestrator.dto.DatasetProvenance;
import com.thesis.orchestrator.dto.DatasetRequest;
import com.thesis.orchestrator.dto.DatasetResponse;
import com.thesis.orchestrator.dto.DatasetStatsResponse;
import com.thesis.orchestrator.dto.DatasetUpdateRequest;
import com.thesis.orchestrator.dto.GraFlexStats;
import com.thesis.orchestrator.dto.GraFlexUploadResponse;
import com.thesis.orchestrator.dto.MeasurementResponse;
import com.thesis.orchestrator.dto.StationResponse;
import com.thesis.orchestrator.exception.InvalidUploadException;
import com.thesis.orchestrator.exception.NotFoundException;
import com.thesis.orchestrator.integration.GraFlexClient;
import com.thesis.orchestrator.repository.DatasetRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);

    private final DatasetRepository datasetRepository;
    private final ObjectMapper objectMapper;
    private final GraFlexClient graFlexClient;
    private final StationCsvZipBuilder stationCsvZipBuilder;

    public DatasetResponse create(DatasetRequest request) {
        Dataset dataset = Dataset.builder()
                .name(request.name())
                .type(request.type())
                .description(request.description())
                .datasetOrigin(DatasetOrigin.UPLOAD)
                .provenance(serializeProvenance(DatasetProvenance.UploadProvenance.empty()))
                .createdAt(Instant.now())
                .build();
        return DatasetResponse.from(datasetRepository.save(dataset));
    }

    /**
     * Creates a dataset from one or more uploaded station CSV files plus a column-role
     * mapping. GraFlex reshapes the raw files (dedup + long-&gt;wide pivot), authors its own
     * schemas, and imports the rows into its own database; the platform stores only
     * metadata — the user's mapping and the GraFlex entity/version pointer — in
     * {@link DatasetProvenance.UploadProvenance}. Station and measurement rows live only in
     * GraFlex and are read back over HTTP when needed (map read-back, download, component
     * execution).
     * Runs in a single transaction: a GraFlex 4xx ({@link InvalidUploadException}) or any
     * persistence failure rolls everything back, leaving no orphaned dataset row.
     */
    @Transactional
    public DatasetResponse uploadStations(
            List<MultipartFile> files, String name, String description,
            String mappingJson, String renamesJson) {
        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new InvalidUploadException("At least one file is required.");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidUploadException("A dataset name is required.");
        }
        if (mappingJson == null || mappingJson.isBlank()) {
            throw new InvalidUploadException("A column-role mapping is required.");
        }

        // Persist the dataset row first to obtain its id, which salts GraFlex's entity
        // ids. Safe because the method is transactional: if the GraFlex call below throws,
        // this insert rolls back too, so no orphaned dataset remains.
        Dataset dataset = datasetRepository.save(Dataset.builder()
                .name(name)
                .type("CSV")
                .description(description)
                .datasetOrigin(DatasetOrigin.UPLOAD)
                .provenance(serializeProvenance(DatasetProvenance.UploadProvenance.empty()))
                .createdAt(Instant.now())
                .build());
        UUID datasetId = dataset.getId();

        // GraFlex reshapes + imports; a 4xx (bad mapping/data) aborts the transaction.
        GraFlexUploadResponse upload = graFlexClient.createDataset(
                files, mappingJson, renamesJson, name, datasetId, "default");

        dataset.setProvenance(serializeProvenance(new DatasetProvenance.UploadProvenance(
                parseNestedStringMap(mappingJson),
                parseNestedStringMap(renamesJson),
                upload.stations().entity(),
                upload.stations().version(),
                upload.measurements().entity(),
                upload.measurements().version(),
                upload.projectId())));
        datasetRepository.save(dataset);

        return DatasetResponse.from(dataset);
    }

    /** Returns a dataset's stations, read from GraFlex via the stored entity pointer. */
    public List<StationResponse> getStations(UUID datasetId) {
        Dataset dataset = findEntity(datasetId);
        DatasetProvenance.UploadProvenance provenance = graflexProvenance(dataset);
        return graFlexClient.readStations(provenance.graflexStationEntity(), null).stream()
                .map(record -> new StationResponse(
                        null,
                        record.stationExternalId(),
                        record.latitude(),
                        record.longitude(),
                        serializeAttributes(record.attributes())))
                .toList();
    }

    /** Returns a dataset's measurements (long format) from GraFlex, capped at {@code limit}. */
    public List<MeasurementResponse> getMeasurements(UUID datasetId, int limit) {
        Dataset dataset = findEntity(datasetId);
        DatasetProvenance.UploadProvenance provenance = graflexProvenance(dataset);
        return graFlexClient.readMeasurements(provenance.graflexMeasurementEntity(), limit).stream()
                .map(record -> new MeasurementResponse(
                        null,
                        record.stationExternalId(),
                        parseTimestamp(record.timestamp()),
                        record.measurementType(),
                        record.value(),
                        record.valueNumeric()))
                .toList();
    }

    /** Returns aggregate counts (stations, measurements) for a dataset, from GraFlex. */
    public DatasetStatsResponse getStats(UUID datasetId) {
        Dataset dataset = findEntity(datasetId);
        DatasetProvenance.UploadProvenance provenance = graflexProvenance(dataset);
        GraFlexStats stats = graFlexClient.readStats(
                provenance.graflexStationEntity(), provenance.graflexMeasurementEntity());
        return new DatasetStatsResponse(stats.stations(), stats.measurements());
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
        datasetRepository.delete(dataset);
    }

    /**
     * Packages a dataset for download. A station-based dataset (data in GraFlex) is returned
     * as a "&lt;name&gt;.zip" archive of {@code stations.csv} + {@code measurements.csv} built
     * from the rows read back from GraFlex. A dataset with no GraFlex entity pointer 404s.
     */
    public DatasetDownload download(UUID id) {
        Dataset dataset = findEntity(id);
        DatasetProvenance.UploadProvenance provenance = tryGraflexProvenance(dataset);
        if (provenance != null) {
            byte[] zip = stationCsvZipBuilder.build(
                    provenance.graflexStationEntity(), provenance.graflexMeasurementEntity());
            return new DatasetDownload(zip, dataset.getName() + ".zip", "application/zip");
        }
        throw new NotFoundException("Dataset has no downloadable content: " + id);
    }

    private String serializeProvenance(DatasetProvenance provenance) {
        try {
            return objectMapper.writeValueAsString(provenance);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    /**
     * Returns a station-based dataset's {@link DatasetProvenance.UploadProvenance},
     * requiring that it points at GraFlex entities. Fails with a 404 otherwise — the read
     * endpoints only make sense for a GraFlex-backed upload.
     */
    private DatasetProvenance.UploadProvenance graflexProvenance(Dataset dataset) {
        DatasetProvenance.UploadProvenance upload = tryGraflexProvenance(dataset);
        if (upload != null) {
            return upload;
        }
        throw new NotFoundException("Dataset is not a GraFlex-backed station upload: " + dataset.getId());
    }

    /**
     * Returns a dataset's {@link DatasetProvenance.UploadProvenance} if it points at GraFlex
     * entities, or {@code null} otherwise (not an upload, no provenance, or unparseable).
     */
    private DatasetProvenance.UploadProvenance tryGraflexProvenance(Dataset dataset) {
        String raw = dataset.getProvenance();
        if (raw != null && !raw.isBlank()) {
            try {
                DatasetProvenance provenance = objectMapper.readValue(raw, DatasetProvenance.class);
                if (provenance instanceof DatasetProvenance.UploadProvenance upload
                        && upload.graflexStationEntity() != null
                        && upload.graflexMeasurementEntity() != null) {
                    return upload;
                }
            } catch (JsonProcessingException ignored) {
                // fall through to null
            }
        }
        return null;
    }

    /** Parses a nested JSON string map ({@code {outer: {inner: value}}}); null when absent. */
    private java.util.Map<String, java.util.Map<String, String>> parseNestedStringMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(
                    json, new TypeReference<java.util.Map<String, java.util.Map<String, String>>>() {
                    });
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
}
