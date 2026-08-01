package com.thesis.orchestrator.service;

import com.thesis.orchestrator.domain.Component;
import com.thesis.orchestrator.dto.ComponentRequest;
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

    public ComponentResponse create(ComponentRequest request) {
        Component component = Component.builder()
                .name(request.name())
                .endpointUrl(request.endpointUrl())
                .inputSchema(request.inputSchema())
                .outputSchema(request.outputSchema())
                .description(request.description())
                .build();
        return ComponentResponse.from(componentRepository.save(component));
    }

    public List<ComponentResponse> getAll() {
        return componentRepository.findAll().stream()
                .map(ComponentResponse::from)
                .toList();
    }

    public ComponentResponse getById(UUID id) {
        return ComponentResponse.from(findEntity(id));
    }

    public ComponentResponse update(UUID id, ComponentRequest request) {
        Component component = findEntity(id);
        component.setName(request.name());
        component.setEndpointUrl(request.endpointUrl());
        component.setInputSchema(request.inputSchema());
        component.setOutputSchema(request.outputSchema());
        component.setDescription(request.description());
        return ComponentResponse.from(componentRepository.save(component));
    }

    public void delete(UUID id) {
        componentRepository.delete(findEntity(id));
    }

    private Component findEntity(UUID id) {
        return componentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Component not found: " + id));
    }
}
