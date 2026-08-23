package com.thesis.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "components")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Component {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String endpointUrl;

    @Column(columnDefinition = "text")
    private String inputSchema;

    @Column(columnDefinition = "text")
    private String outputSchema;

    @Column(columnDefinition = "text")
    private String description;

    /**
     * The measurement inputs this component expects. At execution time the caller maps
     * the dataset's measurement columns onto these names; every expected measurement
     * must be mapped (see the execution flow). Stored relationally, mirroring
     * {@code DataSource.queryParameters}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "component_expected_measurements",
            joinColumns = @JoinColumn(name = "component_id")
    )
    @Column(name = "measurement_name")
    @Builder.Default
    private List<String> expectedMeasurements = new ArrayList<>();
}
