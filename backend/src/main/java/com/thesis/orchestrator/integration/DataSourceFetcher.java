package com.thesis.orchestrator.integration;

import com.thesis.orchestrator.domain.DataSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates mock dataset content for a data source over a requested time period.
 * This stands in for a real external API fetch: no network call is made. Output is
 * either CSV or a GeoJSON FeatureCollection, matching the source's declared format.
 */
@Component
public class DataSourceFetcher {

    // Sofia city-center coordinates used for generated geometry.
    private static final double[][] SOFIA_POINTS = {
            {23.3219, 42.6977},
            {23.3300, 42.7050},
            {23.3150, 42.6900},
    };

    private static final int MAX_ROWS = 30;

    /** Returns generated content in the source's output format for the given period. */
    public String fetch(DataSource source, LocalDate startDate, LocalDate endDate) {
        String metric = metricFor(source);
        if ("CSV".equalsIgnoreCase(source.getOutputFormat())) {
            return generateCsv(metric, startDate, endDate);
        }
        return generateGeoJson(metric);
    }

    /** Chooses a metric name from the source name (traffic vs. air quality), default pm25. */
    private String metricFor(DataSource source) {
        String name = source.getName() == null ? "" : source.getName().toLowerCase();
        if (name.contains("traffic") || name.contains("congestion")) {
            return "congestion";
        }
        return "pm25";
    }

    private String generateCsv(String metric, LocalDate startDate, LocalDate endDate) {
        long days = Math.max(1, ChronoUnit.DAYS.between(startDate, endDate) + 1);
        long rows = Math.min(days, MAX_ROWS);
        StringBuilder sb = new StringBuilder("timestamp,latitude,longitude,").append(metric).append("\n");
        for (long i = 0; i < rows; i++) {
            LocalDate day = startDate.plusDays(i);
            double[] coord = SOFIA_POINTS[(int) (i % SOFIA_POINTS.length)];
            sb.append(day).append("T00:00:00Z,")
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
