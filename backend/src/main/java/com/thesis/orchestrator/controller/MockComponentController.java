package com.thesis.orchestrator.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.thesis.orchestrator.dto.MockExecutionRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-application mock analytical components. These stand in for future external
 * ML services exposed over REST, so the orchestration flow is demoable end-to-end
 * without any real component deployed.
 *
 * <p>Each returns a GeoJSON FeatureCollection. When the request carries an input
 * FeatureCollection, the mock attaches a computed metric to each input geometry;
 * otherwise it falls back to a small static set of Sofia points.
 */
@RestController
@RequestMapping("/mock-components")
public class MockComponentController {

    // Sofia city-center coordinates used for the static fallback.
    private static final double[][] SOFIA_POINTS = {
            {23.3219, 42.6977},
            {23.3300, 42.7050},
            {23.3150, 42.6900},
    };

    @PostMapping("/air-quality")
    public Map<String, Object> airQuality(@Valid @RequestBody MockExecutionRequest request) {
        return featureCollection(request.geoJson(), "pm25", () -> randomInt(10, 60));
    }

    @PostMapping("/traffic")
    public Map<String, Object> traffic(@Valid @RequestBody MockExecutionRequest request) {
        return featureCollection(request.geoJson(), "congestion", this::randomCongestion);
    }

    /**
     * Builds a FeatureCollection. If the input GeoJSON has features, each feature's
     * geometry is reused with a fresh metric value; otherwise static Sofia points
     * are used.
     */
    private Map<String, Object> featureCollection(
            JsonNode input, String metric, ValueSupplier valueSupplier) {
        List<Map<String, Object>> features = new ArrayList<>();

        JsonNode inputFeatures = input == null ? null : input.get("features");
        if (inputFeatures != null && inputFeatures.isArray() && !inputFeatures.isEmpty()) {
            for (JsonNode feature : inputFeatures) {
                JsonNode geometry = feature.get("geometry");
                if (geometry == null || geometry.isNull()) {
                    continue;
                }
                features.add(feature(geometry, metric, valueSupplier.get()));
            }
        }

        if (features.isEmpty()) {
            for (double[] coord : SOFIA_POINTS) {
                Map<String, Object> geometry = Map.of(
                        "type", "Point",
                        "coordinates", List.of(coord[0], coord[1]));
                features.add(feature(geometry, metric, valueSupplier.get()));
            }
        }

        return Map.of("type", "FeatureCollection", "features", features);
    }

    private Map<String, Object> feature(Object geometry, String metric, Object value) {
        return Map.of(
                "type", "Feature",
                "properties", Map.of(metric, value),
                "geometry", geometry);
    }

    private int randomInt(int minInclusive, int maxInclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }

    private double randomCongestion() {
        return Math.round(ThreadLocalRandom.current().nextDouble() * 100.0) / 100.0;
    }

    @FunctionalInterface
    private interface ValueSupplier {
        Object get();
    }
}
