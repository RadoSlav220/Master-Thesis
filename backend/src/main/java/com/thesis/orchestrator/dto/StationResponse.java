package com.thesis.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/** A station belonging to a dataset, exposed to the frontend. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StationResponse(
        UUID id,
        String stationExternalId,
        Double latitude,
        Double longitude,
        String attributes
) {
}
