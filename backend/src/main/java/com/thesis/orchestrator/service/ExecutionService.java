package com.thesis.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.orchestrator.domain.Component;
import com.thesis.orchestrator.domain.Dataset;
import com.thesis.orchestrator.domain.Execution;
import com.thesis.orchestrator.domain.ExecutionStatus;
import com.thesis.orchestrator.dto.ExecutionRequest;
import com.thesis.orchestrator.dto.ExecutionResponse;
import com.thesis.orchestrator.dto.FilterSpec;
import com.thesis.orchestrator.exception.NotFoundException;
import com.thesis.orchestrator.integration.ComponentClient;
import com.thesis.orchestrator.integration.DatasetFilterClient;
import com.thesis.orchestrator.exception.ComponentInvocationException;
import com.thesis.orchestrator.repository.ComponentRepository;
import com.thesis.orchestrator.repository.DatasetRepository;
import com.thesis.orchestrator.repository.ExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final DatasetRepository datasetRepository;
    private final ComponentRepository componentRepository;
    private final ComponentClient componentClient;
    private final DatasetFilterClient datasetFilterClient;
    private final ObjectMapper objectMapper;

    /**
     * Executes a registered component against a dataset. If a filter is provided, the
     * dataset content is first reduced (selected columns/properties + row limit) via the
     * analysis service, and the filtered content is what the component receives. The
     * applied filter is persisted for provenance. The execution is persisted as RUNNING,
     * the component is invoked synchronously, and the record is updated to COMPLETED or
     * FAILED. A failed invocation is still a valid, stored outcome.
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
            String content = applyFilterIfAny(dataset, request.filter(), execution);
            String geoJsonContent = "GEOJSON".equalsIgnoreCase(dataset.getType()) ? content : null;
            JsonNode geoJson = parseGeoJson(geoJsonContent);
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
     * Applies the filter (if present and non-empty) to the dataset content via the
     * analysis service, persisting the spec on the execution. Returns the (possibly
     * filtered) content. Falls back to the raw content when no filter is requested.
     */
    private String applyFilterIfAny(Dataset dataset, FilterSpec filter, Execution execution) {
        String content = dataset.getContent();
        if (filter == null || filter.isEmpty() || content == null || content.isBlank()) {
            return content;
        }
        try {
            execution.setFilterSpec(objectMapper.writeValueAsString(filter));
        } catch (Exception ex) {
            // Non-fatal: provenance serialization failed; continue with filtering.
            execution.setFilterSpec(null);
        }
        String filename = dataset.getName() + DatasetService.extensionFor(dataset.getType());
        return datasetFilterClient.filter(filename, content, filter.columns(), filter.limit());
    }

    private JsonNode parseGeoJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(content);
        } catch (Exception ex) {
            // Stored content failed to parse; send no geometry rather than failing hard.
            return null;
        }
    }

    public List<ExecutionResponse> getAll() {
        return executionRepository.findAll().stream()
                .map(ExecutionResponse::from)
                .toList();
    }

    public ExecutionResponse getById(UUID id) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Execution not found: " + id));
        return ExecutionResponse.from(execution);
    }
}
