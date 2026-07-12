package com.thesis.orchestrator.controller;

import com.thesis.orchestrator.dto.MockExecutionRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-application mock analytical components. These stand in for future external
 * ML services exposed over REST, so the orchestration flow is demoable end-to-end
 * without any real component deployed.
 */
@RestController
@RequestMapping("/mock-components")
public class MockComponentController {

    @PostMapping("/air-quality")
    public Map<String, Object> airQuality(@Valid @RequestBody MockExecutionRequest request) {
        return Map.of(
                "component", "Air Quality",
                "results", List.of(
                        Map.of("latitude", 42.70, "longitude", 23.30, "pm25", randomInt(10, 60)),
                        Map.of("latitude", 42.71, "longitude", 23.31, "pm25", randomInt(10, 60))
                )
        );
    }

    @PostMapping("/traffic")
    public Map<String, Object> traffic(@Valid @RequestBody MockExecutionRequest request) {
        return Map.of(
                "component", "Traffic",
                "results", List.of(
                        Map.of("latitude", 42.70, "longitude", 23.30, "congestion", randomCongestion()),
                        Map.of("latitude", 42.71, "longitude", 23.31, "congestion", randomCongestion())
                )
        );
    }

    private int randomInt(int minInclusive, int maxInclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }

    private double randomCongestion() {
        return Math.round(ThreadLocalRandom.current().nextDouble() * 100.0) / 100.0;
    }
}
