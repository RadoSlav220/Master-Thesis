package com.thesis.orchestrator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.orchestrator.dto.DatasetAnalysisResponse;
import com.thesis.orchestrator.exception.DatasetAnalysisException;
import com.thesis.orchestrator.exception.InvalidDataException;
import org.springframework.beans.factory.annotation.Value;
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

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

/**
 * Sends a dataset file to the external Python dataset-analysis service and returns
 * its structural analysis. The platform stays an orchestration layer: analysis logic
 * lives in the Python service.
 */
@Component
public class DatasetAnalysisClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String serviceUrl;

    public DatasetAnalysisClient(
            RestClient.Builder builder,
            @Value("${dataset.analysis.service.url}") String serviceUrl) {
        // Pin HTTP/1.1: the default JDK client negotiates HTTP/2 via an upgrade that
        // the target Uvicorn server rejects, corrupting the multipart request.
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.restClient = builder
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
        this.serviceUrl = serviceUrl;
    }

    public DatasetAnalysisResponse analyze(String filename, String content) {
        // Build the "file" part with an explicit Content-Disposition (name + filename)
        // so the receiving FastAPI endpoint binds it as an uploaded file. Uses the
        // servlet FormHttpMessageConverter — no reactive dependency required.
        ByteArrayResource resource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentDisposition(
                ContentDisposition.formData().name("file").filename(filename).build());
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, partHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        try {
            return restClient.post()
                    .uri(serviceUrl + "/analyze")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(DatasetAnalysisResponse.class);
        } catch (RestClientResponseException ex) {
            // A 4xx means the service rejected the content as malformed — bad data,
            // not an outage. Surface it as InvalidDataException (-> 400) so callers
            // can fail dataset creation. Any other status is treated as a service failure.
            if (ex.getStatusCode().is4xxClientError()) {
                throw new InvalidDataException("Dataset content is invalid: " + detailOf(ex));
            }
            throw new DatasetAnalysisException(
                    "Dataset analysis service call failed: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new DatasetAnalysisException(
                    "Dataset analysis service call failed: " + ex.getMessage(), ex);
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
