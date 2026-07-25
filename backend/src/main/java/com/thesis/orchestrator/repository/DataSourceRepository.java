package com.thesis.orchestrator.repository;

import com.thesis.orchestrator.domain.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DataSourceRepository extends JpaRepository<DataSource, UUID> {
}
