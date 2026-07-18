package com.thesis.orchestrator.controller;

import com.thesis.orchestrator.dto.DatasetRequest;
import com.thesis.orchestrator.dto.DatasetResponse;
import com.thesis.orchestrator.service.DatasetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetResponse create(@Valid @RequestBody DatasetRequest request) {
        return datasetService.create(request);
    }

    @GetMapping
    public List<DatasetResponse> getAll() {
        return datasetService.getAll();
    }

    @GetMapping("/{id}")
    public DatasetResponse getById(@PathVariable UUID id) {
        return datasetService.getById(id);
    }
}
