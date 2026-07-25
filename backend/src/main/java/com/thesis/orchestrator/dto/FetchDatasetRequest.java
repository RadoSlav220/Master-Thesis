package com.thesis.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record FetchDatasetRequest(
        @NotBlank String name,
        @NotNull Instant startDate,
        @NotNull Instant endDate
) {
}
