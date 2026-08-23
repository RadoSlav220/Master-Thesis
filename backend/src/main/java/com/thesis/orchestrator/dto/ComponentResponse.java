package com.thesis.orchestrator.dto;

import com.thesis.orchestrator.domain.Component;

import java.util.List;
import java.util.UUID;

public record ComponentResponse(
        UUID id,
        String name,
        String endpointUrl,
        String inputSchema,
        String outputSchema,
        String description,
        List<String> expectedMeasurements
) {
    public static ComponentResponse from(Component component) {
        return new ComponentResponse(
                component.getId(),
                component.getName(),
                component.getEndpointUrl(),
                component.getInputSchema(),
                component.getOutputSchema(),
                component.getDescription(),
                component.getExpectedMeasurements()
        );
    }
}
