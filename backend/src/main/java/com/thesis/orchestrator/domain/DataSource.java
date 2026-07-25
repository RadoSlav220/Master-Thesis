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
 * A registered external data source (a mock API for now). Fetching data from a
 * source into a Dataset is a later capability; this entity only records the
 * registration.
 */
@Entity
@Table(name = "data_sources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataSource {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** The kind of source, e.g. API (DATABASE may be added later). */
    @Column(nullable = false)
    private String type;

    /** The data format this source yields, e.g. CSV or GEOJSON. */
    @Column(nullable = false)
    private String outputFormat;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
