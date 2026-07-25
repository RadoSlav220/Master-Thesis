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

@Entity
@Table(name = "datasets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dataset {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(columnDefinition = "text")
    private String description;

    /** Raw dataset content — CSV text or a GeoJSON FeatureCollection. */
    @Column(columnDefinition = "text")
    private String content;

    /** Persisted structure analysis (JSON string from the analysis service). */
    @Column(columnDefinition = "text")
    private String analysisResult;

    /** Dataset type as reported by analysis, e.g. CSV or GEOJSON. */
    @Column
    private String datasetType;

    /** The data source this dataset was fetched from, if any (provenance). */
    @Column
    private UUID sourceId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
