package com.thesis.orchestrator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.orchestrator.dto.GraFlexMeasurement;
import com.thesis.orchestrator.dto.GraFlexStation;
import com.thesis.orchestrator.dto.GraFlexStats;
import com.thesis.orchestrator.dto.GraFlexUploadResponse;
import com.thesis.orchestrator.exception.DatasetAnalysisException;
import com.thesis.orchestrator.exception.InvalidDataException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Talks to GraFlex, the external data platform that stores and processes dataset rows.
 * A station upload sends the raw CSV files + the user's column-role mapping; GraFlex
 * reshapes, authors its own schemas, and imports the rows into its own database. The
 * platform then reads the data back over HTTP — it never touches GraFlex's database
 * directly. Mirrors {@link DatasetAnalysisClient}'s HTTP/1.1-pinned client + multipart
 * construction (the JDK client's HTTP/2 upgrade corrupts multipart to uvicorn).
 */
@Component
public class GraFlexClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String serviceUrl;

    public GraFlexClient(
            RestClient.Builder builder,
            @Value("${graflex.service.url}") String serviceUrl) {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
        this.restClient = builder
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
        this.serviceUrl = serviceUrl;
    }

    /**
     * Uploads the raw station CSV files + a column-role mapping to GraFlex, which
     * reshapes them, authors two entity schemas, and imports the rows. Returns the two
     * entity pointers (name + version) and any warnings.
     *
     * @param files             the raw uploaded CSVs (each a repeated {@code files} part)
     * @param mappingJson       {@code {filename: {column: ROLE}}} as a JSON string
     * @param renamesJson       optional measurement renames JSON; sent only when present
     * @param datasetName       the user-facing dataset name (part of the entity-id stem)
     * @param externalDatasetId the platform dataset id (salts GraFlex's entity ids)
     * @param projectId         the owning GraFlex project
     */
    public GraFlexUploadResponse createDataset(
            List<MultipartFile> files,
            String mappingJson,
            String renamesJson,
            String datasetName,
            UUID externalDatasetId,
            String projectId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            ByteArrayResource resource;
            try {
                resource = new ByteArrayResource(file.getBytes()) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                };
            } catch (IOException ex) {
                throw new DatasetAnalysisException("Could not read uploaded file: " + ex.getMessage(), ex);
            }
            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentDisposition(
                    ContentDisposition.formData().name("files").filename(filename).build());
            body.add("files", new HttpEntity<>(resource, partHeaders));
        }
        body.add("mapping", mappingJson);
        body.add("dataset_name", datasetName);
        body.add("external_dataset_id", externalDatasetId.toString());
        body.add("project_id", projectId);
        if (renamesJson != null && !renamesJson.isBlank()) {
            body.add("renames", renamesJson);
        }

        try {
            return restClient.post()
                    .uri(serviceUrl + "/datasets/station-upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(GraFlexUploadResponse.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw new InvalidDataException("Station upload is invalid: " + detailOf(ex));
            }
            throw new DatasetAnalysisException(
                    "GraFlex station upload failed: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new DatasetAnalysisException(
                    "GraFlex station upload failed: " + ex.getMessage(), ex);
        }
    }

    /** Reads a dataset's stations from GraFlex (for the station map). */
    public List<GraFlexStation> readStations(String stationEntity, Integer limit) {
        String uri = serviceUrl + "/datasets/" + stationEntity + "/stations"
                + (limit != null ? "?limit=" + limit : "");
        return getList(uri, new ParameterizedTypeReference<List<GraFlexStation>>() {
        });
    }

    /** Reads a dataset's measurements from GraFlex (long format, capped at {@code limit}). */
    public List<GraFlexMeasurement> readMeasurements(String measurementEntity, Integer limit) {
        String uri = serviceUrl + "/datasets/" + measurementEntity + "/measurements"
                + (limit != null ? "?limit=" + limit : "");
        return getList(uri, new ParameterizedTypeReference<List<GraFlexMeasurement>>() {
        });
    }

    /** Reads a dataset's aggregate counts (stations, measurements) from GraFlex. */
    public GraFlexStats readStats(String stationEntity, String measurementEntity) {
        String uri = serviceUrl + "/datasets/stats"
                + "?stations_entity=" + stationEntity
                + "&measurements_entity=" + measurementEntity;
        try {
            return restClient.get().uri(uri).retrieve().body(GraFlexStats.class);
        } catch (RestClientResponseException ex) {
            throw new DatasetAnalysisException("GraFlex stats read failed: " + detailOf(ex), ex);
        } catch (Exception ex) {
            throw new DatasetAnalysisException("GraFlex stats read failed: " + ex.getMessage(), ex);
        }
    }

    private <T> List<T> getList(String uri, ParameterizedTypeReference<List<T>> type) {
        try {
            return restClient.get().uri(uri).retrieve().body(type);
        } catch (RestClientResponseException ex) {
            throw new DatasetAnalysisException("GraFlex read failed: " + detailOf(ex), ex);
        } catch (Exception ex) {
            throw new DatasetAnalysisException("GraFlex read failed: " + ex.getMessage(), ex);
        }
    }

    /** Extracts the FastAPI {@code {"detail": ...}} message, falling back to the raw body. */
    private String detailOf(RestClientResponseException ex) {
        try {
            JsonNode body = OBJECT_MAPPER.readTree(ex.getResponseBodyAsString());
            JsonNode detail = body.get("detail");
            if (detail != null && !detail.isNull()) {
                return detail.asText();
            }
        } catch (Exception ignored) {
            // fall through to the raw body
        }
        String raw = ex.getResponseBodyAsString();
        return raw.isBlank() ? ex.getMessage() : raw;
    }
}
