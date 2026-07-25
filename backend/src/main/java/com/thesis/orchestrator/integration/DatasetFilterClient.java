package com.thesis.orchestrator.integration;

import com.thesis.orchestrator.dto.DatasetFilterResponse;
import com.thesis.orchestrator.exception.DatasetFilterException;
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

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Sends a dataset file plus a filter spec (selected columns + optional limit) to the
 * Python service's /filter endpoint and returns the filtered content. Filtering logic
 * lives in the Python service; the platform only orchestrates.
 */
@Component
public class DatasetFilterClient {

    private final RestClient restClient;
    private final String serviceUrl;

    public DatasetFilterClient(
            RestClient.Builder builder,
            @Value("${dataset.analysis.service.url}") String serviceUrl) {
        // Pin HTTP/1.1 (same Uvicorn multipart fix as the analysis client).
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.restClient = builder
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
        this.serviceUrl = serviceUrl;
    }

    /** Returns the filtered dataset content. */
    public String filter(String filename, String content, List<String> columns, Integer limit) {
        ByteArrayResource resource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentDisposition(
                ContentDisposition.formData().name("file").filename(filename).build());
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, partHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);
        body.add("columns", columns == null ? "" : String.join(",", columns));
        if (limit != null) {
            body.add("limit", limit);
        }

        try {
            DatasetFilterResponse response = restClient.post()
                    .uri(serviceUrl + "/filter")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(DatasetFilterResponse.class);
            return response == null ? content : response.content();
        } catch (Exception ex) {
            throw new DatasetFilterException(
                    "Dataset filter service call failed: " + ex.getMessage(), ex);
        }
    }
}
