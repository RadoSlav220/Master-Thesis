package com.thesis.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FetchDatasetRequest(
        @NotBlank String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
