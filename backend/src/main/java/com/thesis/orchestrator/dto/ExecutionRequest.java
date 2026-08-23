package com.thesis.orchestrator.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record ExecutionRequest(
        @NotNull UUID datasetId,
        @NotNull UUID componentId,
        FilterSpec filter,
        /** Component expected-measurement name -> dataset measurement column. */
        Map<String, String> measurementMapping
) {
}
