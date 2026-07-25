package com.thesis.orchestrator.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ExecutionRequest(
        @NotNull UUID datasetId,
        @NotNull UUID componentId,
        FilterSpec filter
) {
}
