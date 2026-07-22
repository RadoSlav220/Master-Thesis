package com.thesis.orchestrator.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight operational endpoints for health checks and build identification.
 * Kept intentionally simple (no Spring Actuator) so the response shapes match the
 * platform's documented contract exactly.
 */
@RestController
public class SystemController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of(
                "name", "Local Digital Twin Platform",
                "version", "0.1.0");
    }
}
