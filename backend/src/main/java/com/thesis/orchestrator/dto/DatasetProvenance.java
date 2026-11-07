package com.thesis.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 * Origin-specific detail about how a dataset was produced. The concrete shape depends on
 * the dataset's {@link com.thesis.orchestrator.domain.DatasetOrigin}. Persisted as a JSON
 * blob on the dataset and returned in the dataset response. Jackson writes/reads a
 * {@code "type"} discriminator so the right subtype is reconstructed.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DatasetProvenance.UploadProvenance.class, name = "UPLOAD")
})
public sealed interface DatasetProvenance {

    /**
     * Manually uploaded, station-based dataset whose rows live in GraFlex. Records the
     * user-authored column-role mapping (and any measurement renames) plus the pointer to
     * the two GraFlex entities (+ versions) the upload created, so the platform can read
     * the data back over HTTP without holding it itself. All fields are nullable: a
     * plain (non-GraFlex) upload uses {@link #empty()}.
     */
    record UploadProvenance(
            Map<String, Map<String, String>> mapping,
            Map<String, Map<String, String>> renames,
            String graflexStationEntity,
            Integer graflexStationVersion,
            String graflexMeasurementEntity,
            Integer graflexMeasurementVersion,
            String projectId) implements DatasetProvenance {

        /** An upload provenance carrying no detail (e.g. a plain CSV upload). */
        public static UploadProvenance empty() {
            return new UploadProvenance(null, null, null, null, null, null, null);
        }
    }
}
