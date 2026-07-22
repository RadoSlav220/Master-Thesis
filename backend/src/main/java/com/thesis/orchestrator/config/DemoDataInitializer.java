package com.thesis.orchestrator.config;

import com.thesis.orchestrator.domain.Component;
import com.thesis.orchestrator.repository.ComponentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;

@org.springframework.stereotype.Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final ComponentRepository componentRepository;

    /** Base URL the registered components point at. The mock endpoints are served by
     *  this same backend, so the default is localhost; override via MOCK_BASE_URL. */
    @Value("${MOCK_BASE_URL:http://localhost:8080}")
    private String mockBaseUrl;

    @Override
    public void run(String... args) {
        register("Air Quality Model", "/mock-components/air-quality",
                "Mock analytical component returning PM2.5 values as GeoJSON.");
        register("Traffic Model", "/mock-components/traffic",
                "Mock analytical component returning congestion values as GeoJSON.");
    }

    private void register(String name, String path, String description) {
        String endpointUrl = mockBaseUrl + path;
        if (componentRepository.existsByEndpointUrl(endpointUrl)) {
            return;
        }
        componentRepository.save(Component.builder()
                .name(name)
                .endpointUrl(endpointUrl)
                .description(description)
                .build());
        log.info("Demo bootstrap: registered component '{}' -> {}", name, endpointUrl);
    }
}
