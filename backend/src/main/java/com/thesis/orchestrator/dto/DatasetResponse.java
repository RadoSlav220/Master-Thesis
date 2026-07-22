package com.thesis.orchestrator.dto;

import com.thesis.orchestrator.domain.Dataset;

import java.time.Instant;
import java.util.UUID;

public record DatasetResponse(
        UUID id,
        String name,
        String type,
        String description,
        boolean hasGeoJson,
        Instant createdAt
) {
    public static DatasetResponse from(Dataset dataset) {
        String geo = dataset.getGeoJsonContent();
        return new DatasetResponse(
                dataset.getId(),
                dataset.getName(),
                dataset.getType(),
                dataset.getDescription(),
                geo != null && !geo.isBlank(),
                dataset.getCreatedAt()
        );
    }
}
