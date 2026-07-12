package com.thesis.orchestrator.integration;

import com.thesis.orchestrator.exception.ComponentInvocationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Invokes an external analytical component over REST. The platform acts purely as
 * an orchestration layer: it POSTs the dataset reference to the component's
 * endpoint and returns the raw JSON response body, which is persisted verbatim.
 */
@Component
public class ComponentClient {

    private final RestClient restClient;

    public ComponentClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public String invoke(String endpointUrl, UUID datasetId) {
        try {
            return restClient.post()
                    .uri(endpointUrl)
                    .body(Map.of("datasetId", datasetId))
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            throw new ComponentInvocationException(
                    "Failed to invoke component at " + endpointUrl + ": " + ex.getMessage(), ex);
        }
    }
}
