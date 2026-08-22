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

import java.util.UUID;

/**
 * A measuring station belonging to an uploaded dataset. Deduplicated by
 * {@code stationExternalId} within a dataset. References its dataset by a bare
 * {@code datasetId} UUID (mirrors {@link Dataset#getSourceId()}) rather than a JPA
 * relationship; cascade delete is handled in the service layer.
 */
@Entity
@Table(name = "stations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Station {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID datasetId;

    /** The station identifier as it appears in the uploaded file. */
    @Column(nullable = false)
    private String stationExternalId;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    /** Extra station-attribute columns as a JSON object string; null if none. */
    @Column(columnDefinition = "text")
    private String attributes;
}
