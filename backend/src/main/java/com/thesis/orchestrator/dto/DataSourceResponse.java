package com.thesis.orchestrator.dto;

import com.thesis.orchestrator.domain.DataSource;
import com.thesis.orchestrator.domain.DataSourceType;
import com.thesis.orchestrator.domain.QueryParameterDefinition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DataSourceResponse(
        UUID id,
        String name,
        DataSourceType type,
        String outputFormat,
        String description,
        List<QueryParameter> queryParameters,
        Instant createdAt
) {
    public static DataSourceResponse from(DataSource dataSource) {
        List<QueryParameter> params = dataSource.getQueryParameters().stream()
                .map(DataSourceResponse::toDto)
                .toList();
        return new DataSourceResponse(
                dataSource.getId(),
                dataSource.getName(),
                dataSource.getType(),
                dataSource.getOutputFormat(),
                dataSource.getDescription(),
                params,
                dataSource.getCreatedAt()
        );
    }

    private static QueryParameter toDto(QueryParameterDefinition definition) {
        return new QueryParameter(
                definition.getName(),
                definition.isRequired(),
                definition.getDefaultValue()
        );
    }
}
