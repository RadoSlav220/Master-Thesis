package com.thesis.orchestrator.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MockExecutionRequest(
        @NotNull UUID datasetId,
        JsonNode geoJson
) {
}
