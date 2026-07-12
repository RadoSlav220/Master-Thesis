package com.thesis.orchestrator.service;

import com.thesis.orchestrator.domain.Execution;
import com.thesis.orchestrator.domain.ExecutionStatus;
import com.thesis.orchestrator.dto.ExecutionRequest;
import com.thesis.orchestrator.dto.ExecutionResponse;
import com.thesis.orchestrator.exception.NotFoundException;
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

    /**
     * Creates a placeholder execution record. No external service is called yet:
     * the execution is persisted with status CREATED and an empty result.
     */
    @Transactional
    public ExecutionResponse create(ExecutionRequest request) {
        if (!datasetRepository.existsById(request.datasetId())) {
            throw new NotFoundException("Dataset not found: " + request.datasetId());
        }
        if (!componentRepository.existsById(request.componentId())) {
            throw new NotFoundException("Component not found: " + request.componentId());
        }

        Execution execution = Execution.builder()
                .datasetId(request.datasetId())
                .componentId(request.componentId())
                .status(ExecutionStatus.CREATED)
                .createdAt(Instant.now())
                .build();
        return ExecutionResponse.from(executionRepository.save(execution));
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
