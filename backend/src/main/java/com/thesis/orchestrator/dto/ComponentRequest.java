package com.thesis.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;

public record ComponentRequest(
        @NotBlank String name,
        @NotBlank String endpointUrl,
        String inputSchema,
        String outputSchema,
        String description
) {
}
