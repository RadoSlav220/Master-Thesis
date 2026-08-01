package com.thesis.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single query parameter a data source's API expects, stored as an owned value in the
 * source's {@code queryParameters} element collection. {@code required} flags whether the
 * API demands it; {@code defaultValue} is optional.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QueryParameterDefinition {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean required;

    @Column
    private String defaultValue;
}
