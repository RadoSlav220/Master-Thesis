package com.thesis.orchestrator.repository;

import com.thesis.orchestrator.domain.Station;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StationRepository extends JpaRepository<Station, UUID> {

    List<Station> findByDatasetId(UUID datasetId);

    void deleteByDatasetId(UUID datasetId);
}
