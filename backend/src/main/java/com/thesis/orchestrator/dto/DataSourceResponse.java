package com.thesis.orchestrator.dto;

import com.thesis.orchestrator.domain.DataSource;

import java.time.Instant;
import java.util.UUID;

public record DataSourceResponse(
        UUID id,
        String name,
        String type,
        String outputFormat,
        String description,
        Instant createdAt
) {
    public static DataSourceResponse from(DataSource dataSource) {
        return new DataSourceResponse(
                dataSource.getId(),
                dataSource.getName(),
                dataSource.getType(),
                dataSource.getOutputFormat(),
                dataSource.getDescription(),
                dataSource.getCreatedAt()
        );
    }
}
