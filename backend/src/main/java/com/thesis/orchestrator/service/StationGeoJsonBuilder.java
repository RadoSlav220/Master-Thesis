package com.thesis.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thesis.orchestrator.domain.Measurement;
import com.thesis.orchestrator.domain.Station;
import com.thesis.orchestrator.repository.MeasurementRepository;
import com.thesis.orchestrator.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds a GeoJSON FeatureCollection from a station-based dataset (relational
 * {@code stations} + {@code measurements}) for consumption by an analytical component.
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

    private final StationRepository stationRepository;
    private final MeasurementRepository measurementRepository;
    private final ObjectMapper objectMapper;

    /** The distinct measurement column names present in a dataset. */
    public Set<String> distinctMeasurementTypes(UUID datasetId) {
        return measurementRepository.findAllByDatasetId(datasetId).stream()
                .map(Measurement::getMeasurementType)
                .collect(Collectors.toSet());
    }

    /**
     * Builds the FeatureCollection. {@code mapping} maps each component expected-measurement
     * name to the dataset {@code measurementType} that supplies it. Stations without
     * coordinates are skipped (they have no geometry to place on the map).
     */
    public JsonNode build(UUID datasetId, Map<String, String> mapping) {
        List<Station> stations = stationRepository.findByDatasetId(datasetId);

        // stationExternalId -> measurementType -> readings (sorted by timestamp, nulls last)
        Map<String, Map<String, List<Measurement>>> byStation =
                measurementRepository.findAllByDatasetId(datasetId).stream()
                        .collect(Collectors.groupingBy(
                                Measurement::getStationExternalId,
                                Collectors.groupingBy(Measurement::getMeasurementType)));

        ArrayNode features = objectMapper.createArrayNode();
        for (Station station : stations) {
            if (station.getLatitude() == null || station.getLongitude() == null) {
                continue;
            }
            ObjectNode feature = objectMapper.createObjectNode();
            feature.put("type", "Feature");

            ObjectNode geometry = objectMapper.createObjectNode();
            geometry.put("type", "Point");
            ArrayNode coordinates = objectMapper.createArrayNode();
            coordinates.add(station.getLongitude());
            coordinates.add(station.getLatitude());
            geometry.set("coordinates", coordinates);
            feature.set("geometry", geometry);

            ObjectNode properties = objectMapper.createObjectNode();
            properties.put("stationId", station.getStationExternalId());

            Map<String, List<Measurement>> stationMeasurements =
                    byStation.getOrDefault(station.getStationExternalId(), Map.of());
            mapping.forEach((expectedName, measurementType) -> {
                List<Measurement> readings = stationMeasurements.getOrDefault(measurementType, List.of());
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
    private ArrayNode series(List<Measurement> readings) {
        ArrayNode array = objectMapper.createArrayNode();
        readings.stream()
                .sorted(Comparator.comparing(Measurement::getTimestamp,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(m -> {
                    ObjectNode point = objectMapper.createObjectNode();
                    Instant timestamp = m.getTimestamp();
                    point.put("t", timestamp == null ? null : timestamp.toString());
                    if (m.getValueNumeric() != null) {
                        point.put("v", m.getValueNumeric());
                    } else {
                        point.put("v", m.getValue());
                    }
                    array.add(point);
                });
        return array;
    }
}
