package com.thesis.orchestrator.dto;

import com.thesis.orchestrator.domain.Execution;

import java.time.Instant;
import java.util.UUID;

public record ExecutionResponse(
        UUID id,
        UUID datasetId,
        UUID componentId,
        String status,
        String result,
        Instant createdAt,
        Instant finishedAt
) {
    public static ExecutionResponse from(Execution execution) {
        return new ExecutionResponse(
                execution.getId(),
                execution.getDatasetId(),
                execution.getComponentId(),
                execution.getStatus().name(),
                execution.getResult(),
                execution.getCreatedAt(),
                execution.getFinishedAt()
        );
    }
}
