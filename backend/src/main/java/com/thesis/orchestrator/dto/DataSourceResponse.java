package com.thesis.orchestrator.dto;

import com.thesis.orchestrator.domain.DataSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DataSourceResponse(
        UUID id,
        String name,
        String type,
        String outputFormat,
        String description,
        List<QueryParameter> queryParameters,
        Instant createdAt
) {
    public static DataSourceResponse from(DataSource dataSource, List<QueryParameter> queryParameters) {
        return new DataSourceResponse(
                dataSource.getId(),
                dataSource.getName(),
                dataSource.getType(),
                dataSource.getOutputFormat(),
                dataSource.getDescription(),
                queryParameters,
                dataSource.getCreatedAt()
        );
    }
}
