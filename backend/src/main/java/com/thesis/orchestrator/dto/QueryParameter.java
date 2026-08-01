package com.thesis.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A single query parameter a data source's API expects. Defined at registration
 * time and later populated when building the outgoing fetch request. {@code required}
 * flags whether the API demands it; {@code defaultValue} is optional.
 */
public record QueryParameter(
        @NotBlank String name,
        @NotNull Boolean required,
        String defaultValue
) {
}
