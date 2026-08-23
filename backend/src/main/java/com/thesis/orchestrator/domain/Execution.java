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
@Table(name = "executions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Execution {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID datasetId;

    @Column(nullable = false)
    private UUID componentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    @Column(columnDefinition = "text")
    private String result;

    @Column(columnDefinition = "text")
    private String errorMessage;

    /** The filter applied before invoking the component, if any (JSON, provenance). */
    @Column(columnDefinition = "text")
    private String filterSpec;

    /** The measurement mapping applied before invoking the component, if any (JSON, provenance). */
    @Column(columnDefinition = "text")
    private String measurementMapping;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant finishedAt;
}
