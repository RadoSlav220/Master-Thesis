package com.thesis.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One measurement reading as GraFlex returns it from the measurement read endpoint.
 * GraFlex unpivots its wide storage back to this long shape (one record per
 * station/timestamp/type), matching the platform's {@link MeasurementResponse}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraFlexMeasurement(
        String stationExternalId,
        String timestamp,
        String measurementType,
        String value,
        Double valueNumeric) {
}
