package com.thesis.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record FetchDatasetRequest(
        @NotBlank String name,
        Map<String, String> queryParameters
) {
}
