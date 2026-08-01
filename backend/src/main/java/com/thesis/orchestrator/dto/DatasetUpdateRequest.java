package com.thesis.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Editable metadata for an existing dataset. Only name and description can change —
 * content, analysis, origin, and provenance are immutable (a dataset is a snapshot).
 */
public record DatasetUpdateRequest(
        @NotBlank String name,
        String description
) {
}
