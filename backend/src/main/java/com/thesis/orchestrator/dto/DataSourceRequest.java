package com.thesis.orchestrator.dto;

import com.thesis.orchestrator.domain.DataSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DataSourceRequest(
        @NotBlank String name,
        @NotNull DataSourceType type,
        @NotBlank String outputFormat,
        String endpointUrl,
        String apiKey,
        String description,
        @Valid List<QueryParameter> queryParameters
) {
}
