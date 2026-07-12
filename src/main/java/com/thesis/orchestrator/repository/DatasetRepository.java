package com.thesis.orchestrator.repository;

import com.thesis.orchestrator.domain.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DatasetRepository extends JpaRepository<Dataset, UUID> {
}
