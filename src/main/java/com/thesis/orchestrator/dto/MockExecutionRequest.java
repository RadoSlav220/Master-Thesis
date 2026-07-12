package com.thesis.orchestrator.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MockExecutionRequest(
        @NotNull UUID datasetId
) {
}
