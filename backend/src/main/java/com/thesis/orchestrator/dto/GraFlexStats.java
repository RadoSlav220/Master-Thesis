package com.thesis.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Aggregate counts for a GraFlex-backed dataset: the number of stations and the number
 * of long-format measurements (non-null measure cells across the wide table).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraFlexStats(long stations, long measurements) {
}
