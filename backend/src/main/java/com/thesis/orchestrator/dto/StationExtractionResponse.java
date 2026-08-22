package com.thesis.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * The relational shape returned by the Python analysis service's
 * {@code /extract-stations} endpoint: deduplicated stations plus long-format
 * measurements, with any non-fatal warnings (e.g. conflicting coordinates).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StationExtractionResponse(
        List<StationRecord> stations,
        List<MeasurementRecord> measurements,
        List<String> warnings
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StationRecord(
            String stationExternalId,
            Double latitude,
            Double longitude,
            Map<String, String> attributes
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MeasurementRecord(
            String stationExternalId,
            String timestamp,
            String measurementType,
            String value,
            Double valueNumeric
    ) {
    }
}
