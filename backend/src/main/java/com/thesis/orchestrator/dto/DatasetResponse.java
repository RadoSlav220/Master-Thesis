package com.thesis.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.orchestrator.domain.Dataset;
import com.thesis.orchestrator.domain.DatasetOrigin;

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
        DatasetOrigin datasetOrigin,
        DatasetProvenance provenance,
        Instant createdAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
                dataset.getDatasetOrigin(),
                parseProvenance(dataset.getProvenance()),
                dataset.getCreatedAt()
        );
    }

    /** Deserializes the stored provenance JSON into its typed shape; null when absent. */
    private static DatasetProvenance parseProvenance(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, DatasetProvenance.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
