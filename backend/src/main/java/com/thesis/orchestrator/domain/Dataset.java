package com.thesis.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** Where this dataset came from; drives the provenance shape. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DatasetOrigin datasetOrigin;

    /** Origin-specific provenance detail as JSON (see DatasetProvenance); null if unknown. */
    @Column(columnDefinition = "text")
    private String provenance;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
