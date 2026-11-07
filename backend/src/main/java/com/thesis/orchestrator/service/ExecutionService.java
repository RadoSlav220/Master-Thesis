package com.thesis.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.orchestrator.domain.Component;
import com.thesis.orchestrator.domain.Dataset;
import com.thesis.orchestrator.domain.Execution;
import com.thesis.orchestrator.domain.ExecutionStatus;
import com.thesis.orchestrator.dto.DatasetProvenance;
import com.thesis.orchestrator.dto.ExecutionDownload;
import com.thesis.orchestrator.dto.ExecutionRequest;
import com.thesis.orchestrator.dto.ExecutionResponse;
import com.thesis.orchestrator.exception.InvalidExecutionException;
import com.thesis.orchestrator.exception.NotFoundException;
import com.thesis.orchestrator.integration.ComponentClient;
import com.thesis.orchestrator.exception.ComponentInvocationException;
import com.thesis.orchestrator.repository.ComponentRepository;
import com.thesis.orchestrator.repository.DatasetRepository;
import com.thesis.orchestrator.repository.ExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final DatasetRepository datasetRepository;
    private final ComponentRepository componentRepository;
    private final ComponentClient componentClient;
    private final StationGeoJsonBuilder stationGeoJsonBuilder;
    private final ObjectMapper objectMapper;

    /**
     * Executes a registered component against a dataset. All datasets are station-based
     * (relational, stored in GraFlex): the component's expected measurements are mapped onto
     * the dataset's measurement columns and a GeoJSON FeatureCollection is synthesized. The
     * execution is persisted as RUNNING, the component is invoked synchronously, and the
     * record is updated to COMPLETED or FAILED. A failed invocation is still a valid, stored
     * outcome.
     */
    @Transactional
    public ExecutionResponse create(ExecutionRequest request) {
        Dataset dataset = datasetRepository.findById(request.datasetId())
                .orElseThrow(() -> new NotFoundException("Dataset not found: " + request.datasetId()));
        Component component = componentRepository.findById(request.componentId())
                .orElseThrow(() -> new NotFoundException("Component not found: " + request.componentId()));

        Execution execution = Execution.builder()
                .datasetId(request.datasetId())
                .componentId(request.componentId())
                .status(ExecutionStatus.RUNNING)
                .createdAt(Instant.now())
                .build();
        executionRepository.save(execution);

        try {
            // All datasets are station-based (relational, stored in GraFlex): map the
            // component's expected measurements onto the dataset's measurement columns, then
            // synthesize a GeoJSON FeatureCollection (per-station time series in feature
            // properties). The rows are read from GraFlex via the dataset's entity pointers.
            DatasetProvenance.UploadProvenance provenance = graflexProvenance(dataset);
            Map<String, String> mapping = validateMeasurementMapping(
                    provenance.graflexMeasurementEntity(), component, request.measurementMapping());
            persistMeasurementMapping(execution, mapping);
            JsonNode geoJson = stationGeoJsonBuilder.build(
                    provenance.graflexStationEntity(), provenance.graflexMeasurementEntity(), mapping);
            String result = componentClient.invoke(component.getEndpointUrl(), request.datasetId(), geoJson);
            execution.setResult(result);
            execution.setStatus(ExecutionStatus.COMPLETED);
        } catch (ComponentInvocationException ex) {
            execution.setErrorMessage(ex.getMessage());
            execution.setStatus(ExecutionStatus.FAILED);
        }
        execution.setFinishedAt(Instant.now());

        return ExecutionResponse.from(executionRepository.save(execution));
    }

    /**
     * Validates the caller's measurement mapping against the component's expected
     * measurements and the dataset's available measurement columns. Every expected
     * measurement must be mapped to a measurement column that exists in the dataset;
     * otherwise a 400 is raised (blocking the run). Returns the validated mapping,
     * restricted to the component's expected measurements.
     */
    private Map<String, String> validateMeasurementMapping(
            String measurementEntity, Component component, Map<String, String> requested) {
        List<String> expected = component.getExpectedMeasurements();
        if (expected == null || expected.isEmpty()) {
            return Map.of();
        }
        Map<String, String> mapping = requested == null ? Map.of() : requested;
        Set<String> availableTypes = stationGeoJsonBuilder.distinctMeasurementTypes(measurementEntity);

        List<String> unmapped = expected.stream()
                .filter(name -> {
                    String column = mapping.get(name);
                    return column == null || column.isBlank() || !availableTypes.contains(column);
                })
                .toList();
        if (!unmapped.isEmpty()) {
            throw new InvalidExecutionException(
                    "The dataset cannot supply these expected measurements (map them to a "
                            + "measurement column present in the dataset): " + String.join(", ", unmapped));
        }
        return expected.stream()
                .collect(java.util.stream.Collectors.toMap(name -> name, mapping::get));
    }

    private void persistMeasurementMapping(Execution execution, Map<String, String> mapping) {
        if (mapping.isEmpty()) {
            return;
        }
        try {
            execution.setMeasurementMapping(objectMapper.writeValueAsString(mapping));
        } catch (Exception ex) {
            // Non-fatal: provenance serialization failed; continue with the run.
            execution.setMeasurementMapping(null);
        }
    }

    /**
     * Resolves a station-based dataset's GraFlex entity pointers from its stored
     * {@link DatasetProvenance.UploadProvenance}. A 400 is raised (blocking the run) when the
     * dataset has no GraFlex pointer — such a dataset cannot supply rows to a component.
     */
    private DatasetProvenance.UploadProvenance graflexProvenance(Dataset dataset) {
        String raw = dataset.getProvenance();
        if (raw != null && !raw.isBlank()) {
            try {
                DatasetProvenance provenance = objectMapper.readValue(raw, DatasetProvenance.class);
                if (provenance instanceof DatasetProvenance.UploadProvenance upload
                        && upload.graflexStationEntity() != null
                        && upload.graflexMeasurementEntity() != null) {
                    return upload;
                }
            } catch (Exception ignored) {
                // fall through to the invalid-execution below
            }
        }
        throw new InvalidExecutionException(
                "Dataset is not a GraFlex-backed station upload and cannot be executed: " + dataset.getId());
    }

    public List<ExecutionResponse> getAll() {
        return executionRepository.findAll().stream()
                .map(ExecutionResponse::from)
                .toList();
    }

    public ExecutionResponse getById(UUID id) {
        return ExecutionResponse.from(findEntity(id));
    }

    /**
     * Returns an execution's result packaged for download. Component results are GeoJSON
     * FeatureCollections, so the file is a ".geojson" with "application/geo+json" content
     * type, named after the execution's id. 404s when the execution has no result yet
     * (i.e. it is not COMPLETED).
     */
    public ExecutionDownload download(UUID id) {
        Execution execution = findEntity(id);
        String result = execution.getResult();
        if (result == null || result.isBlank()) {
            throw new NotFoundException("Execution has no downloadable result: " + id);
        }
        String filename = "execution-" + shortId(execution.getId()) + ".geojson";
        return new ExecutionDownload(result, filename, "application/geo+json");
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    public void delete(UUID id) {
        executionRepository.delete(findEntity(id));
    }

    private Execution findEntity(UUID id) {
        return executionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Execution not found: " + id));
    }
}
