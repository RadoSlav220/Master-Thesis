package com.thesis.orchestrator.repository;

import com.thesis.orchestrator.domain.Component;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ComponentRepository extends JpaRepository<Component, UUID> {
}
