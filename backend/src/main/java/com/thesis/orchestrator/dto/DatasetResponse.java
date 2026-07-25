package com.thesis.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.thesis.orchestrator.domain.Dataset;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatasetResponse(
        UUID id,
        String name,
        String type,
        String description,
        boolean hasGeoJson,
        String datasetType,
        String analysisResult,
        UUID sourceId,
        Instant createdAt
) {
    public static DatasetResponse from(Dataset dataset) {
        String content = dataset.getContent();
        boolean hasContent = content != null && !content.isBlank();
        boolean isGeoJson = hasContent && "GEOJSON".equalsIgnoreCase(dataset.getType());
        return new DatasetResponse(
                dataset.getId(),
                dataset.getName(),
                dataset.getType(),
                dataset.getDescription(),
                isGeoJson,
                dataset.getDatasetType(),
                dataset.getAnalysisResult(),
                dataset.getSourceId(),
                dataset.getCreatedAt()
        );
    }
}
