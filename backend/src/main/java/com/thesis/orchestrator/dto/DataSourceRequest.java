package com.thesis.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;

public record DataSourceRequest(
        @NotBlank String name,
        @NotBlank String type,
        @NotBlank String outputFormat,
        String description
) {
}
