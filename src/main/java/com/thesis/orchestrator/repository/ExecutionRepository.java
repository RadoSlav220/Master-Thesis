package com.thesis.orchestrator.repository;

import com.thesis.orchestrator.domain.Execution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {
}
