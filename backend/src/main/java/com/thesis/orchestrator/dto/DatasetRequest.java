package com.thesis.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;

public record DatasetRequest(
        @NotBlank String name,
        @NotBlank String type,
        String description
) {
}
