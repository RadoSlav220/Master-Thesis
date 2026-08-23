package com.thesis.orchestrator.dto;

/** Aggregate counts for a station-based dataset, exposed to the frontend. */
public record DatasetStatsResponse(
        long stationCount,
        long measurementCount
) {
}
