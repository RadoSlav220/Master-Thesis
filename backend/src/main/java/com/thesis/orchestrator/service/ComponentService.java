package com.thesis.orchestrator.service;

import com.thesis.orchestrator.domain.Component;
import com.thesis.orchestrator.dto.ComponentResponse;
import com.thesis.orchestrator.exception.NotFoundException;
import com.thesis.orchestrator.repository.ComponentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ComponentService {

    private final ComponentRepository componentRepository;

    public List<ComponentResponse> getAll() {
        return componentRepository.findAll().stream()
                .map(ComponentResponse::from)
                .toList();
    }

    public ComponentResponse getById(UUID id) {
        return ComponentResponse.from(findEntity(id));
    }

    private Component findEntity(UUID id) {
        return componentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Component not found: " + id));
    }
}
