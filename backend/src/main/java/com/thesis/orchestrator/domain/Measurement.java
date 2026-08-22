package com.thesis.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single measurement reading in long format: one row per station, timestamp and
 * measurement column. Belongs to an uploaded dataset (bare {@code datasetId} UUID).
 * {@code value} holds the verbatim cell; {@code valueNumeric} is populated when the
 * cell parses as a number.
 */
@Entity
@Table(name = "measurements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Measurement {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID datasetId;

    @Column(nullable = false)
    private String stationExternalId;

    @Column
    private Instant timestamp;

    /** The measurement column name from the uploaded file. */
    @Column(nullable = false)
    private String measurementType;

    @Column(columnDefinition = "text")
    private String value;

    @Column
    private Double valueNumeric;
}
