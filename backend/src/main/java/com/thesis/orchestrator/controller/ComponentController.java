package com.thesis.orchestrator.controller;

import com.thesis.orchestrator.dto.ComponentResponse;
import com.thesis.orchestrator.service.ComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/components")
@RequiredArgsConstructor
public class ComponentController {

    private final ComponentService componentService;

    @GetMapping
    public List<ComponentResponse> getAll() {
        return componentService.getAll();
    }

    @GetMapping("/{id}")
    public ComponentResponse getById(@PathVariable UUID id) {
        return componentService.getById(id);
    }
}
