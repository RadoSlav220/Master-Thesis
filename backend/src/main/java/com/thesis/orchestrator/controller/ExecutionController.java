package com.thesis.orchestrator.controller;

import com.thesis.orchestrator.dto.ExecutionRequest;
import com.thesis.orchestrator.dto.ExecutionResponse;
import com.thesis.orchestrator.service.ExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionResponse create(@Valid @RequestBody ExecutionRequest request) {
        return executionService.create(request);
    }

    @GetMapping
    public List<ExecutionResponse> getAll() {
        return executionService.getAll();
    }

    @GetMapping("/{id}")
    public ExecutionResponse getById(@PathVariable UUID id) {
        return executionService.getById(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        executionService.delete(id);
    }
}
