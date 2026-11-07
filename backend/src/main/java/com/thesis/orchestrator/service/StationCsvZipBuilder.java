package com.thesis.orchestrator.service;

import com.thesis.orchestrator.dto.GraFlexMeasurement;
import com.thesis.orchestrator.dto.GraFlexStation;
import com.thesis.orchestrator.exception.DatasetAnalysisException;
import com.thesis.orchestrator.integration.GraFlexClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Reconstructs a station-based dataset as a zip of two CSVs for download. The station and
 * measurement rows are read from GraFlex over HTTP (via the dataset's entity pointers), not
 * from the platform database.
 *
 * <p>The CSV headers mirror the station-upload role names so the archive round-trips:
 * unzipping and re-uploading the two files through Create Dataset reproduces the dataset
 * (the upload UI name-guesses the roles).
 *
 * <ul>
 *   <li><b>stations.csv</b> — one row per station:
 *       {@code stationId,latitude,longitude,<attr...>}. Attribute columns are the sorted
 *       union of every station's extra-attribute keys.</li>
 *   <li><b>measurements.csv</b> — wide, one row per {@code (stationId, timestamp)}:
 *       {@code stationId,timestamp,<type1>,<type2>,...}. A cell holds that type's value
 *       for the station+timestamp, blank when absent.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class StationCsvZipBuilder {

    private final GraFlexClient graFlexClient;

    /** Builds the {@code stations.csv} + {@code measurements.csv} archive for a dataset. */
    public byte[] build(String stationEntity, String measurementEntity) {
        List<GraFlexStation> stations = graFlexClient.readStations(stationEntity, null);
        List<GraFlexMeasurement> measurements = graFlexClient.readMeasurements(measurementEntity, null);

        byte[] stationsCsv = buildStationsCsv(stations);
        byte[] measurementsCsv = buildMeasurementsCsv(measurements);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("stations.csv"));
            zip.write(stationsCsv);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("measurements.csv"));
            zip.write(measurementsCsv);
            zip.closeEntry();
        } catch (IOException ex) {
            throw new DatasetAnalysisException("Could not build dataset archive: " + ex.getMessage(), ex);
        }
        return out.toByteArray();
    }

    private byte[] buildStationsCsv(List<GraFlexStation> stations) {
        // Sorted union of attribute keys across all stations, for stable columns.
        TreeSet<String> attrKeys = new TreeSet<>();
        for (GraFlexStation station : stations) {
            if (station.attributes() != null) {
                attrKeys.addAll(station.attributes().keySet());
            }
        }

        StringBuilder sb = new StringBuilder();
        List<String> header = new java.util.ArrayList<>(List.of("stationId", "latitude", "longitude"));
        header.addAll(attrKeys);
        writeRow(sb, header);

        for (GraFlexStation station : stations) {
            Map<String, String> attrs = station.attributes() == null ? Map.of() : station.attributes();
            List<String> row = new java.util.ArrayList<>();
            row.add(station.stationExternalId());
            row.add(station.latitude() == null ? "" : String.valueOf(station.latitude()));
            row.add(station.longitude() == null ? "" : String.valueOf(station.longitude()));
            for (String key : attrKeys) {
                row.add(attrs.getOrDefault(key, ""));
            }
            writeRow(sb, row);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildMeasurementsCsv(List<GraFlexMeasurement> measurements) {
        // Sorted distinct measurement types -> wide columns.
        TreeSet<String> types = new TreeSet<>();
        // (stationId, timestamp) -> (type -> value); last write wins on a collision.
        Map<String, Map<String, String>> rows = new java.util.LinkedHashMap<>();
        for (GraFlexMeasurement m : measurements) {
            types.add(m.measurementType());
            String ts = m.timestamp() == null ? "" : m.timestamp();
            String key = m.stationExternalId() + "\u0000" + ts;
            rows.computeIfAbsent(key, k -> new TreeMap<>()).put(m.measurementType(), m.value());
        }

        StringBuilder sb = new StringBuilder();
        List<String> header = new java.util.ArrayList<>(List.of("stationId", "timestamp"));
        header.addAll(types);
        writeRow(sb, header);

        for (Map.Entry<String, Map<String, String>> entry : rows.entrySet()) {
            String[] parts = entry.getKey().split("\u0000", 2);
            String stationId = parts[0];
            String timestamp = parts.length > 1 ? parts[1] : "";
            Map<String, String> values = entry.getValue();
            List<String> row = new java.util.ArrayList<>();
            row.add(stationId);
            row.add(timestamp);
            for (String type : types) {
                row.add(values.getOrDefault(type, ""));
            }
            writeRow(sb, row);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Writes one CSV row (RFC-4180 minimal escaping) plus a trailing newline. */
    private void writeRow(StringBuilder sb, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(fields.get(i)));
        }
        sb.append('\n');
    }

    /** Quotes a field when it contains a comma, quote, or newline; doubles embedded quotes. */
    private String escape(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return '"' + field.replace("\"", "\"\"") + '"';
        }
        return field;
    }
}
