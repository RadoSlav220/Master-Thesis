package com.thesis.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.thesis.orchestrator.domain.Measurement;

import java.time.Instant;
import java.util.UUID;

/** A single long-format measurement reading, exposed to the frontend. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeasurementResponse(
        UUID id,
        String stationExternalId,
        Instant timestamp,
        String measurementType,
        String value,
        Double valueNumeric
) {
    public static MeasurementResponse from(Measurement measurement) {
        return new MeasurementResponse(
                measurement.getId(),
                measurement.getStationExternalId(),
                measurement.getTimestamp(),
                measurement.getMeasurementType(),
                measurement.getValue(),
                measurement.getValueNumeric()
        );
    }
}
