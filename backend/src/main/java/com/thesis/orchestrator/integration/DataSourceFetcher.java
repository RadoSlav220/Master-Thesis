package com.thesis.orchestrator.integration;

import com.thesis.orchestrator.domain.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates mock dataset content for a data source over a requested time window.
 * This stands in for a real external API fetch: no network call is made. Output is
 * either CSV or a GeoJSON FeatureCollection, matching the source's declared format.
 * The window is expressed as UTC instants, so timestamps identify exact moments.
 */
@Component
public class DataSourceFetcher {

    private static final Logger log = LoggerFactory.getLogger(DataSourceFetcher.class);

    // Sofia city-center coordinates used for generated geometry.
    private static final double[][] SOFIA_POINTS = {
            {23.3219, 42.6977},
            {23.3300, 42.7050},
            {23.3150, 42.6900},
    };

    private static final int MAX_ROWS = 30;

    // Common parameter-name conventions a source might register for a time window.
    private static final List<String> START_KEYS = List.of("startDate", "start", "from");
    private static final List<String> END_KEYS = List.of("endDate", "end", "to");

    // Default window when the source has no parseable date parameters: the last 24 hours.
    private static final long DEFAULT_WINDOW_HOURS = 24;

    /**
     * Returns generated content in the source's output format. The supplied query
     * parameters are the values the caller populated for the source's registered
     * parameters. When they include parseable start/end instants (see {@link #START_KEYS}
     * / {@link #END_KEYS}) the CSV time window is derived from them; otherwise a default
     * window is used. (A real fetch will use the parameters to shape the outgoing request.)
     */
    public String fetch(DataSource source, Map<String, String> queryParameters) {
        if (queryParameters != null && !queryParameters.isEmpty()) {
            log.debug("Fetching from source {} with query parameters {}", source.getId(), queryParameters);
        }
        String metric = metricFor(source);
        if ("CSV".equalsIgnoreCase(source.getOutputFormat())) {
            Instant end = parseInstant(queryParameters, END_KEYS).orElse(Instant.now());
            Instant start = parseInstant(queryParameters, START_KEYS)
                    .orElse(end.minus(DEFAULT_WINDOW_HOURS, ChronoUnit.HOURS));
            // Guard against an inverted window from arbitrary user-supplied values.
            if (start.isAfter(end)) {
                start = end.minus(DEFAULT_WINDOW_HOURS, ChronoUnit.HOURS);
            }
            return generateCsv(metric, start, end);
        }
        return generateGeoJson(metric);
    }

    /** Reads the first present key from {@code keys} and parses it as an ISO-8601 instant. */
    private java.util.Optional<Instant> parseInstant(Map<String, String> queryParameters, List<String> keys) {
        if (queryParameters == null) {
            return java.util.Optional.empty();
        }
        for (String key : keys) {
            String value = queryParameters.get(key);
            if (value != null && !value.isBlank()) {
                try {
                    return java.util.Optional.of(Instant.parse(value.trim()));
                } catch (DateTimeException ex) {
                    log.debug("Query parameter {}={} is not a parseable instant; ignoring", key, value);
                }
            }
        }
        return java.util.Optional.empty();
    }

    /** Chooses a metric name from the source name (traffic vs. air quality), default pm25. */
    private String metricFor(DataSource source) {
        String name = source.getName() == null ? "" : source.getName().toLowerCase();
        if (name.contains("traffic") || name.contains("congestion")) {
            return "congestion";
        }
        return "pm25";
    }

    /**
     * Produces one row per evenly-spaced instant across [startDate, endDate], up to
     * MAX_ROWS rows. Each timestamp is an exact UTC moment (ISO-8601, e.g. ...Z).
     */
    private String generateCsv(String metric, Instant startDate, Instant endDate) {
        long totalMinutes = Math.max(0, ChronoUnit.MINUTES.between(startDate, endDate));
        long rows = Math.min(MAX_ROWS, Math.max(1, totalMinutes == 0 ? 1 : totalMinutes / 60 + 1));
        long stepMinutes = rows > 1 ? totalMinutes / (rows - 1) : 0;

        StringBuilder sb = new StringBuilder("timestamp,latitude,longitude,").append(metric).append("\n");
        for (long i = 0; i < rows; i++) {
            Instant ts = startDate.plus(stepMinutes * i, ChronoUnit.MINUTES);
            double[] coord = SOFIA_POINTS[(int) (i % SOFIA_POINTS.length)];
            sb.append(ts).append(",")
                    .append(coord[1]).append(",")
                    .append(coord[0]).append(",")
                    .append(value(metric))
                    .append("\n");
        }
        return sb.toString();
    }

    private String generateGeoJson(String metric) {
        StringBuilder features = new StringBuilder();
        for (int i = 0; i < SOFIA_POINTS.length; i++) {
            double[] coord = SOFIA_POINTS[i];
            if (i > 0) {
                features.append(",");
            }
            features.append("""
                    {"type":"Feature","properties":{"%s":%s},\
                    "geometry":{"type":"Point","coordinates":[%s,%s]}}\
                    """.formatted(metric, value(metric), coord[0], coord[1]));
        }
        return "{\"type\":\"FeatureCollection\",\"features\":[" + features + "]}";
    }

    private String value(String metric) {
        if ("congestion".equals(metric)) {
            return String.valueOf(Math.round(ThreadLocalRandom.current().nextDouble() * 100.0) / 100.0);
        }
        return String.valueOf(ThreadLocalRandom.current().nextInt(10, 61));
    }
}
