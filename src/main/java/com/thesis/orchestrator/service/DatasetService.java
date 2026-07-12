package com.thesis.orchestrator.service;

import com.thesis.orchestrator.domain.Dataset;
import com.thesis.orchestrator.dto.DatasetRequest;
import com.thesis.orchestrator.dto.DatasetResponse;
import com.thesis.orchestrator.exception.NotFoundException;
import com.thesis.orchestrator.repository.DatasetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DatasetService {

    private final DatasetRepository datasetRepository;

    public DatasetResponse create(DatasetRequest request) {
        Dataset dataset = Dataset.builder()
                .name(request.name())
                .type(request.type())
                .description(request.description())
                .createdAt(Instant.now())
                .build();
        return DatasetResponse.from(datasetRepository.save(dataset));
    }

    public List<DatasetResponse> getAll() {
        return datasetRepository.findAll().stream()
                .map(DatasetResponse::from)
                .toList();
    }

    public DatasetResponse getById(UUID id) {
        Dataset dataset = datasetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dataset not found: " + id));
        return DatasetResponse.from(dataset);
    }
}
