package com.thesis.orchestrator.controller;

import com.thesis.orchestrator.dto.DataSourceRequest;
import com.thesis.orchestrator.dto.DataSourceResponse;
import com.thesis.orchestrator.dto.DatasetResponse;
import com.thesis.orchestrator.dto.FetchDatasetRequest;
import com.thesis.orchestrator.service.DataSourceService;
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
@RequestMapping("/data-sources")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService dataSourceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DataSourceResponse create(@Valid @RequestBody DataSourceRequest request) {
        return dataSourceService.create(request);
    }

    @GetMapping
    public List<DataSourceResponse> getAll() {
        return dataSourceService.getAll();
    }

    @GetMapping("/{id}")
    public DataSourceResponse getById(@PathVariable UUID id) {
        return dataSourceService.getById(id);
    }

    @PostMapping("/{id}/fetch")
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetResponse fetch(@PathVariable UUID id, @Valid @RequestBody FetchDatasetRequest request) {
        return dataSourceService.fetch(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        dataSourceService.delete(id);
    }

    @PutMapping("/{id}")
    public DataSourceResponse update(@PathVariable UUID id, @Valid @RequestBody DataSourceRequest request) {
        return dataSourceService.update(id, request);
    }
}
