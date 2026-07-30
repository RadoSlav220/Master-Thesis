package com.thesis.orchestrator.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    /** The kind of source (API, or DATABASE later). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataSourceType type;

    /** The data format this source yields, e.g. CSV or GEOJSON. */
    @Column(nullable = false)
    private String outputFormat;

    @Column(columnDefinition = "text")
    private String description;

    /** Query parameters the source's API expects (owned value collection). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "data_source_query_parameters",
            joinColumns = @JoinColumn(name = "data_source_id")
    )
    @Builder.Default
    private List<QueryParameterDefinition> queryParameters = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
