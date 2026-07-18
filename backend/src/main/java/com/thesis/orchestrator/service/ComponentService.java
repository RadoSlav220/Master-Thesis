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
        Component component = componentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Component not found: " + id));
        return ComponentResponse.from(component);
    }
}
