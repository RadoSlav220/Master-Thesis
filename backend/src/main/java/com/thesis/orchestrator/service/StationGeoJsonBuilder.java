package com.thesis.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thesis.orchestrator.dto.GraFlexMeasurement;
import com.thesis.orchestrator.dto.GraFlexStation;
import com.thesis.orchestrator.integration.GraFlexClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a GeoJSON FeatureCollection from a station-based dataset for consumption by an
 * analytical component. The station and measurement rows are read from GraFlex over HTTP
 * (via the dataset's stored entity pointers), not from the platform database.
 *
 * <p>One point Feature per station (coords -&gt; geometry). Because a component may be a
 * predictive/time-series model, each mapped measurement is carried as the <em>full time
 * series</em> of readings — an array of {@code {t, v}} objects sorted by timestamp — in
 * the feature's {@code properties}, keyed by the component's expected measurement name.
 * The mapping is {@code expectedName -> dataset measurementType column}.
 */
@Service
@RequiredArgsConstructor
public class StationGeoJsonBuilder {

    private static final Logger log = LoggerFactory.getLogger(StationGeoJsonBuilder.class);

    private final GraFlexClient graFlexClient;
    private final ObjectMapper objectMapper;

    /** The distinct measurement column names present in a dataset's GraFlex entity. */
    public Set<String> distinctMeasurementTypes(String measurementEntity) {
        return graFlexClient.readMeasurements(measurementEntity, null).stream()
                .map(GraFlexMeasurement::measurementType)
                .collect(Collectors.toSet());
    }

    /**
     * Builds the FeatureCollection. {@code mapping} maps each component expected-measurement
     * name to the dataset {@code measurementType} that supplies it. Stations without
     * coordinates are skipped (they have no geometry to place on the map).
     */
    public JsonNode build(String stationEntity, String measurementEntity, Map<String, String> mapping) {
        List<GraFlexStation> stations = graFlexClient.readStations(stationEntity, null);

        // stationExternalId -> measurementType -> readings
        Map<String, Map<String, List<GraFlexMeasurement>>> byStation =
                graFlexClient.readMeasurements(measurementEntity, null).stream()
                        .collect(Collectors.groupingBy(
                                GraFlexMeasurement::stationExternalId,
                                Collectors.groupingBy(GraFlexMeasurement::measurementType)));

        ArrayNode features = objectMapper.createArrayNode();
        for (GraFlexStation station : stations) {
            if (station.latitude() == null || station.longitude() == null) {
                continue;
            }
            ObjectNode feature = objectMapper.createObjectNode();
            feature.put("type", "Feature");

            ObjectNode geometry = objectMapper.createObjectNode();
            geometry.put("type", "Point");
            ArrayNode coordinates = objectMapper.createArrayNode();
            coordinates.add(station.longitude());
            coordinates.add(station.latitude());
            geometry.set("coordinates", coordinates);
            feature.set("geometry", geometry);

            ObjectNode properties = objectMapper.createObjectNode();
            properties.put("stationId", station.stationExternalId());

            Map<String, List<GraFlexMeasurement>> stationMeasurements =
                    byStation.getOrDefault(station.stationExternalId(), Map.of());
            mapping.forEach((expectedName, measurementType) -> {
                List<GraFlexMeasurement> readings = stationMeasurements.getOrDefault(measurementType, List.of());
                properties.set(expectedName, series(readings));
            });

            feature.set("properties", properties);
            features.add(feature);
        }

        ObjectNode collection = objectMapper.createObjectNode();
        collection.put("type", "FeatureCollection");
        collection.set("features", features);
        return collection;
    }

    /** Builds the sorted [{t, v}, ...] series array for a station's readings of one type. */
    private ArrayNode series(List<GraFlexMeasurement> readings) {
        ArrayNode array = objectMapper.createArrayNode();
        readings.stream()
                .sorted(Comparator.comparing(m -> parseTimestamp(m.timestamp()),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(m -> {
                    ObjectNode point = objectMapper.createObjectNode();
                    point.put("t", m.timestamp());
                    if (m.valueNumeric() != null) {
                        point.put("v", m.valueNumeric());
                    } else {
                        point.put("v", m.value());
                    }
                    array.add(point);
                });
        return array;
    }

    /** Parses an ISO-8601 timestamp (offset or instant) for sorting; null when unparseable. */
    private Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ex) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored) {
                log.warn("Could not parse measurement timestamp '{}'", value);
                return null;
            }
        }
    }
}
