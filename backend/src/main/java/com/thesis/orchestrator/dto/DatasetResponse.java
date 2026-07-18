package com.thesis.orchestrator.dto;

import com.thesis.orchestrator.domain.Dataset;

import java.time.Instant;
import java.util.UUID;

public record DatasetResponse(
        UUID id,
        String name,
        String type,
        String description,
        Instant createdAt
) {
    public static DatasetResponse from(Dataset dataset) {
        return new DatasetResponse(
                dataset.getId(),
                dataset.getName(),
                dataset.getType(),
                dataset.getDescription(),
                dataset.getCreatedAt()
        );
    }
}
