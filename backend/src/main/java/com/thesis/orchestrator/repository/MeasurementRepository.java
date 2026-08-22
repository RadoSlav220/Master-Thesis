package com.thesis.orchestrator.repository;

import com.thesis.orchestrator.domain.Measurement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MeasurementRepository extends JpaRepository<Measurement, UUID> {

    List<Measurement> findByDatasetId(UUID datasetId, Pageable pageable);

    void deleteByDatasetId(UUID datasetId);
}
