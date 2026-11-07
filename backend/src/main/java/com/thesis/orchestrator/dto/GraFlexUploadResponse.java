package com.thesis.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GraFlex's response to a station upload: the two entity pointers (id + version) the
 * upload created, plus any non-fatal warnings. The platform stores the pointers in
 * {@link DatasetProvenance.UploadProvenance} so it can read the data back over HTTP.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraFlexUploadResponse(
        @JsonProperty("project_id") String projectId,
        EntityPointer stations,
        EntityPointer measurements,
        java.util.List<String> warnings) {

    /** A pointer to one GraFlex entity version (its name + version number). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EntityPointer(String entity, Integer version) {
    }
}
