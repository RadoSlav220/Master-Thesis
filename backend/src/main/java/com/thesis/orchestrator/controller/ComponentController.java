package com.thesis.orchestrator.controller;

import com.thesis.orchestrator.dto.ComponentRequest;
import com.thesis.orchestrator.dto.ComponentResponse;
import com.thesis.orchestrator.service.ComponentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/components")
@RequiredArgsConstructor
public class ComponentController {

    private final ComponentService componentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComponentResponse create(@Valid @RequestBody ComponentRequest request) {
        return componentService.create(request);
    }

    @GetMapping
    public List<ComponentResponse> getAll() {
        return componentService.getAll();
    }

    @GetMapping("/{id}")
    public ComponentResponse getById(@PathVariable UUID id) {
        return componentService.getById(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        componentService.delete(id);
    }

    @PutMapping("/{id}")
    public ComponentResponse update(@PathVariable UUID id, @Valid @RequestBody ComponentRequest request) {
        return componentService.update(id, request);
    }
}
