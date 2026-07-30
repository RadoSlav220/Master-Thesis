package com.thesis.orchestrator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record DataSourceRequest(
        @NotBlank String name,
        @NotBlank String type,
        @NotBlank String outputFormat,
        String description,
        @Valid List<QueryParameter> queryParameters
) {
}
