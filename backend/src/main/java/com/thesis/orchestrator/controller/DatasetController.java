package com.thesis.orchestrator.controller;

import com.thesis.orchestrator.dto.DatasetAnalysisResponse;
import com.thesis.orchestrator.dto.DatasetDownload;
import com.thesis.orchestrator.dto.DatasetRequest;
import com.thesis.orchestrator.dto.DatasetResponse;
import com.thesis.orchestrator.dto.DatasetStatsResponse;
import com.thesis.orchestrator.dto.DatasetUpdateRequest;
import com.thesis.orchestrator.dto.MeasurementResponse;
import com.thesis.orchestrator.dto.StationResponse;
import com.thesis.orchestrator.service.DatasetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description) {
        return datasetService.upload(file, name, description);
    }

    @PostMapping(value = "/upload-stations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetResponse uploadStations(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("mapping") String mapping,
            @RequestParam(value = "renames", required = false) String renames) {
        return datasetService.uploadStations(files, name, description, mapping, renames);
    }

    @GetMapping("/{id}/stations")
    public List<StationResponse> getStations(@PathVariable UUID id) {
        return datasetService.getStations(id);
    }

    @GetMapping("/{id}/measurements")
    public List<MeasurementResponse> getMeasurements(
            @PathVariable UUID id,
            @RequestParam(value = "limit", defaultValue = "500") int limit) {
        return datasetService.getMeasurements(id, limit);
    }

    @GetMapping("/{id}/stats")
    public DatasetStatsResponse getStats(@PathVariable UUID id) {
        return datasetService.getStats(id);
    }

    @GetMapping
    public List<DatasetResponse> getAll() {
        return datasetService.getAll();
    }

    @GetMapping("/{id}")
    public DatasetResponse getById(@PathVariable UUID id) {
        return datasetService.getById(id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        DatasetDownload download = datasetService.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.filename()).build().toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .body(download.content());
    }

    @PostMapping("/{id}/analyze")
    public DatasetAnalysisResponse analyze(@PathVariable UUID id) {
        return datasetService.analyze(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        datasetService.delete(id);
    }

    @PutMapping("/{id}")
    public DatasetResponse update(@PathVariable UUID id, @Valid @RequestBody DatasetUpdateRequest request) {
        return datasetService.update(id, request);
    }
}
