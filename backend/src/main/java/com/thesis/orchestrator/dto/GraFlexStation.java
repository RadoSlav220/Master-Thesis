package com.thesis.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * One station row as GraFlex returns it from the station read endpoint. The
 * {@code attributes} map holds the station's {@code metadata}-role columns, which
 * GraFlex re-nests out of the flat table for us.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraFlexStation(
        String stationExternalId,
        Double latitude,
        Double longitude,
        Map<String, String> attributes) {
}
