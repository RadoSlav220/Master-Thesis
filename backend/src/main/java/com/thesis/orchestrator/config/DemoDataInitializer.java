package com.thesis.orchestrator.config;

import com.thesis.orchestrator.domain.Component;
import com.thesis.orchestrator.domain.DataSource;
import com.thesis.orchestrator.domain.DataSourceType;
import com.thesis.orchestrator.domain.QueryParameterDefinition;
import com.thesis.orchestrator.repository.ComponentRepository;
import com.thesis.orchestrator.repository.DataSourceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;

import java.time.Instant;
import java.util.List;

@org.springframework.stereotype.Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final ComponentRepository componentRepository;
    private final DataSourceRepository dataSourceRepository;

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
        registerUsgsEarthquakeSource();
        registerGeoapifyIsolineSource();
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

    /**
     * Registers a real external data source that returns GeoJSON: the free, no-key USGS
     * Earthquake API. Its response is a GeoJSON FeatureCollection stored verbatim, exercising
     * the generic fetch machinery. Defaults query recent global M4.5+ events so the map shows
     * features (a Sofia bounding box would return an empty collection — earthquakes there are rare).
     */
    private void registerUsgsEarthquakeSource() {
        String name = "Global Earthquakes (USGS)";
        if (dataSourceRepository.existsByName(name)) {
            return;
        }
        List<QueryParameterDefinition> params = List.of(
                new QueryParameterDefinition("format", true, "geojson"),
                new QueryParameterDefinition("starttime", true, "2026-08-01"),
                new QueryParameterDefinition("minmagnitude", false, "4.5"),
                new QueryParameterDefinition("limit", false, "50")
        );
        dataSourceRepository.save(DataSource.builder()
                .name(name)
                .type(DataSourceType.API)
                .outputFormat("GEOJSON")
                .endpointUrl("https://earthquake.usgs.gov/fdsnws/event/1/query")
                .description("Recent global earthquakes from the USGS FDSN API, returned as GeoJSON.")
                .queryParameters(new java.util.ArrayList<>(params))
                .createdAt(Instant.now())
                .build());
        log.info("Demo bootstrap: registered data source '{}'", name);
    }

    /**
     * Registers the Geoapify Isoline API as a demo data source. Returns a GeoJSON
     * FeatureCollection of MultiPolygon features — one per range value — showing the
     * area reachable from a point within the given time/distance. Defaults to three
     * drive-time rings (5 / 10 / 15 min) centred on Sofia's city centre.
     */
    private void registerGeoapifyIsolineSource() {
        String name = "Sofia Reachability Zones (Geoapify)";
        if (dataSourceRepository.existsByName(name)) {
            return;
        }
        List<QueryParameterDefinition> params = List.of(
                new QueryParameterDefinition("lat", true, "42.6977"),
                new QueryParameterDefinition("lon", true, "23.3219"),
                new QueryParameterDefinition("type", true, "time"),
                new QueryParameterDefinition("mode", true, "drive"),
                new QueryParameterDefinition("range", true, "300,600,900")
        );
        dataSourceRepository.save(DataSource.builder()
                .name(name)
                .type(DataSourceType.API)
                .outputFormat("GEOJSON")
                .endpointUrl("https://api.geoapify.com/v1/isoline")
                .apiKey("b294f01772f14855afc8c4cfc10fa17e")
                .description("Drive-time reachability polygons from a point, powered by the Geoapify Isoline API. Returns one MultiPolygon per range step (default: 5/10/15 min from Sofia centre).")
                .queryParameters(new java.util.ArrayList<>(params))
                .createdAt(Instant.now())
                .build());
        log.info("Demo bootstrap: registered data source '{}'", name);
    }
}
