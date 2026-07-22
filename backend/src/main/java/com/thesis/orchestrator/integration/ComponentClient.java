package com.thesis.orchestrator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.thesis.orchestrator.exception.ComponentInvocationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Invokes an external analytical component over REST. The platform acts purely as
 * an orchestration layer: it POSTs the dataset reference (and its GeoJSON content,
 * when available) to the component's endpoint and returns the raw JSON response
 * body, which is persisted verbatim.
 */
@Component
public class ComponentClient {

    private final RestClient restClient;

    public ComponentClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public String invoke(String endpointUrl, UUID datasetId, JsonNode geoJson) {
        Map<String, Object> body = new HashMap<>();
        body.put("datasetId", datasetId);
        body.put("geoJson", geoJson); // null is serialized as JSON null
        try {
            return restClient.post()
                    .uri(endpointUrl)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            throw new ComponentInvocationException(
                    "Failed to invoke component at " + endpointUrl + ": " + ex.getMessage(), ex);
        }
    }
}
