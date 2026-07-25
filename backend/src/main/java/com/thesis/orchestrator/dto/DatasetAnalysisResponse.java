package com.thesis.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Dataset structure analysis relayed from the Python analysis service. CSV
 * datasets populate {@code columns}; GeoJSON datasets populate {@code properties}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatasetAnalysisResponse(
        String datasetType,
        List<String> columns,
        List<String> properties
) {
}
