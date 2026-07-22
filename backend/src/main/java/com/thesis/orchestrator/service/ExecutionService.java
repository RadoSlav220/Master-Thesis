package com.thesis.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.orchestrator.domain.Component;
import com.thesis.orchestrator.domain.Dataset;
import com.thesis.orchestrator.domain.Execution;
import com.thesis.orchestrator.domain.ExecutionStatus;
import com.thesis.orchestrator.dto.ExecutionRequest;
import com.thesis.orchestrator.dto.ExecutionResponse;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final DatasetRepository datasetRepository;
    private final ComponentRepository componentRepository;
    private final ComponentClient componentClient;
    private final ObjectMapper objectMapper;

    /**
     * Executes a registered component against a dataset. The execution is persisted
     * as RUNNING, the external component endpoint is invoked synchronously (receiving
     * the dataset's GeoJSON content when available), and the record is updated to
     * COMPLETED (with the result) or FAILED (with an error message). A failed
     * invocation is still a valid, stored outcome.
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
            JsonNode geoJson = parseGeoJson(dataset.getGeoJsonContent());
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
