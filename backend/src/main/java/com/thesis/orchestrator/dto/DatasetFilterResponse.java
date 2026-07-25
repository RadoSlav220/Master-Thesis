package com.thesis.orchestrator.dto;

/** Envelope returned by the Python /filter endpoint: filtered content + its type. */
public record DatasetFilterResponse(
        String datasetType,
        String content
) {
}
