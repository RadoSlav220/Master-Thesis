package com.thesis.orchestrator.integration;

import com.thesis.orchestrator.domain.DataSource;
import com.thesis.orchestrator.exception.DataSourceFetchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fetches dataset content for a data source. When the source declares an
 * {@code endpointUrl}, a real HTTP GET is issued (the registered query-parameter values are
 * appended as the query string) and the response body is stored verbatim, so sources are
 * expected to return pipeline-ready content (a GeoJSON FeatureCollection or CSV). When no URL
 * is present, synthetic mock content is generated instead (the original behaviour), so legacy
 * sources keep working.
 */
@Component
public class DataSourceFetcher {

    private static final Logger log = LoggerFactory.getLogger(DataSourceFetcher.class);

    private final RestClient restClient;

    public DataSourceFetcher(RestClient.Builder builder) {
        // A plain client suffices for a simple GET; the HTTP/1.1 pin used by the analysis
        // client exists only to work around multipart corruption talking to uvicorn.
        this.restClient = builder.build();
    }

    // Sofia city-center coordinates used for generated (mock) geometry.
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
     * Returns dataset content in the source's output format. If the source has an
     * {@code endpointUrl}, performs a real HTTP GET and returns the response body verbatim;
     * otherwise falls back to mock generation. The supplied query parameters are the resolved
     * values for the source's registered parameters (the fetch dialog prefills defaults), so
     * they are used verbatim as the outgoing query string.
     */
    public String fetch(DataSource source, Map<String, String> queryParameters) {
        String endpointUrl = source.getEndpointUrl();
        if (endpointUrl == null || endpointUrl.isBlank()) {
            return mockContent(source, queryParameters);
        }
        return httpFetch(source, endpointUrl, queryParameters);
    }

    /** Performs the real GET and returns the response body verbatim. */
    private String httpFetch(DataSource source, String endpointUrl, Map<String, String> queryParameters) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(endpointUrl)
                .queryParams(toMultiValueMap(queryParameters));
        // Append the stored API key as a query parameter if the source has one and the
        // caller hasn't already supplied a value for "apiKey".
        String apiKey = source.getApiKey();
        if (apiKey != null && !apiKey.isBlank()
                && (queryParameters == null || !queryParameters.containsKey("apiKey"))) {
            uriBuilder.queryParam("apiKey", apiKey);
        }
        URI uri = uriBuilder.encode(StandardCharsets.UTF_8).build().toUri();
        log.debug("Fetching from source {} at {}", source.getId(), uri);
        String rawBody;
        try {
            rawBody = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientResponseException ex) {
            throw new DataSourceFetchException(
                    "Data source returned " + ex.getStatusCode() + ": " + ex.getMessage(), ex);
        } catch (RestClientException ex) {
            throw new DataSourceFetchException(
                    "Failed to reach data source at " + endpointUrl + ": " + ex.getMessage(), ex);
        }
        if (rawBody == null) {
            throw new DataSourceFetchException("Data source at " + endpointUrl + " returned an empty body", null);
        }
        return rawBody;
    }

    /** Copies non-null, non-blank query-parameter values into a MultiValueMap for URI building. */
    private MultiValueMap<String, String> toMultiValueMap(Map<String, String> queryParameters) {
        MultiValueMap<String, String> multi = new LinkedMultiValueMap<>();
        if (queryParameters != null) {
            queryParameters.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    multi.add(key, value);
                }
            });
        }
        return multi;
    }

    // --- Mock generation (fallback when the source has no endpoint URL) ---------------------

    private String mockContent(DataSource source, Map<String, String> queryParameters) {
        if (queryParameters != null && !queryParameters.isEmpty()) {
            log.debug("Mock-fetching from source {} with query parameters {}", source.getId(), queryParameters);
        }
        String metric = metricFor(source);
        Instant end = parseInstant(queryParameters, END_KEYS).orElse(Instant.now());
        Instant start = parseInstant(queryParameters, START_KEYS)
                .orElse(end.minus(DEFAULT_WINDOW_HOURS, ChronoUnit.HOURS));
        // Guard against an inverted window from arbitrary user-supplied values.
        if (start.isAfter(end)) {
            start = end.minus(DEFAULT_WINDOW_HOURS, ChronoUnit.HOURS);
        }
        return generateCsv(metric, start, end);
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

    private String value(String metric) {
        if ("congestion".equals(metric)) {
            return String.valueOf(Math.round(ThreadLocalRandom.current().nextDouble() * 100.0) / 100.0);
        }
        return String.valueOf(ThreadLocalRandom.current().nextInt(10, 61));
    }
}
