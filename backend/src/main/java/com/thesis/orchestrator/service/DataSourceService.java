package com.thesis.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.orchestrator.domain.DataSource;
import com.thesis.orchestrator.domain.Dataset;
import com.thesis.orchestrator.domain.DatasetOrigin;
import com.thesis.orchestrator.domain.QueryParameterDefinition;
import com.thesis.orchestrator.dto.DataSourceRequest;
import com.thesis.orchestrator.dto.DataSourceResponse;
import com.thesis.orchestrator.dto.DatasetAnalysisResponse;
import com.thesis.orchestrator.dto.DatasetProvenance;
import com.thesis.orchestrator.dto.DatasetResponse;
import com.thesis.orchestrator.dto.FetchDatasetRequest;
import com.thesis.orchestrator.dto.QueryParameter;
import com.thesis.orchestrator.exception.NotFoundException;
import com.thesis.orchestrator.integration.DataSourceFetcher;
import com.thesis.orchestrator.integration.DatasetAnalysisClient;
import com.thesis.orchestrator.repository.DataSourceRepository;
import com.thesis.orchestrator.repository.DatasetRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataSourceService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceService.class);

    private final DataSourceRepository dataSourceRepository;
    private final DatasetRepository datasetRepository;
    private final DataSourceFetcher dataSourceFetcher;
    private final DatasetAnalysisClient datasetAnalysisClient;
    private final ObjectMapper objectMapper;

    public DataSourceResponse create(DataSourceRequest request) {
        DataSource dataSource = DataSource.builder()
                .name(request.name())
                .type(request.type())
                .outputFormat(request.outputFormat())
                .description(request.description())
                .queryParameters(toDefinitions(request.queryParameters()))
                .createdAt(Instant.now())
                .build();
        return DataSourceResponse.from(dataSourceRepository.save(dataSource));
    }

    public List<DataSourceResponse> getAll() {
        return dataSourceRepository.findAll().stream()
                .map(DataSourceResponse::from)
                .toList();
    }

    public DataSourceResponse getById(UUID id) {
        return DataSourceResponse.from(findEntity(id));
    }

    /**
     * Deletes a data source. Datasets previously fetched from it are left untouched
     * (their {@code sourceId} becomes a dangling reference) — fetched snapshots are
     * immutable and stand on their own. The source's query-parameter definitions
     * (element collection) are removed with it by the JPA lifecycle.
     */
    public void delete(UUID id) {
        dataSourceRepository.delete(findEntity(id));
    }

    private List<QueryParameterDefinition> toDefinitions(List<QueryParameter> params) {
        if (params == null) {
            return new ArrayList<>();
        }
        return params.stream()
                .map(p -> new QueryParameterDefinition(p.name(), p.required(), p.defaultValue()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String serializeFetchProvenance(DatasetProvenance provenance) {
        try {
            return objectMapper.writeValueAsString(provenance);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize dataset provenance: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Fetches a dataset snapshot from a data source using the supplied query-parameter
     * values (the source's registered parameters, populated by the caller), stores it,
     * then runs analysis once and persists the result. The fetched snapshot is
     * immutable, so caching its analysis is safe. Analysis failure does not fail the
     * fetch — the dataset is still stored (with null analysis).
     */
    public DatasetResponse fetch(UUID sourceId, FetchDatasetRequest request) {
        DataSource source = findEntity(sourceId);

        String content = dataSourceFetcher.fetch(source, request.queryParameters());
        String format = source.getOutputFormat();

        Dataset dataset = Dataset.builder()
                .name(request.name())
                .type(format)
                .description("Fetched from data source: " + source.getName())
                .content(content)
                .sourceId(sourceId)
                .datasetOrigin(DatasetOrigin.API)
                .provenance(serializeFetchProvenance(new DatasetProvenance.ApiProvenance(request.queryParameters())))
                .createdAt(Instant.now())
                .build();
        datasetRepository.save(dataset);

        try {
            String filename = request.name() + DatasetService.extensionFor(format);
            DatasetAnalysisResponse analysis = datasetAnalysisClient.analyze(filename, content);
            dataset.setDatasetType(analysis.datasetType());
            dataset.setAnalysisResult(objectMapper.writeValueAsString(analysis));
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize analysis for fetched dataset {}: {}", dataset.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("Analysis failed for fetched dataset {}: {}", dataset.getId(), ex.getMessage());
        }

        return DatasetResponse.from(datasetRepository.save(dataset));
    }

    private DataSource findEntity(UUID id) {
        return dataSourceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Data source not found: " + id));
    }
}
