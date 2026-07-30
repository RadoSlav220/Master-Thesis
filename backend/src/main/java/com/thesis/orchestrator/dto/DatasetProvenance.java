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
        @JsonSubTypes.Type(value = DatasetProvenance.ApiProvenance.class, name = "API"),
        @JsonSubTypes.Type(value = DatasetProvenance.DatabaseProvenance.class, name = "DATABASE"),
        @JsonSubTypes.Type(value = DatasetProvenance.UploadProvenance.class, name = "UPLOAD")
})
public sealed interface DatasetProvenance {

    /** Dataset fetched from a data source's HTTP API: the query-parameter values used. */
    record ApiProvenance(Map<String, String> queryParameters) implements DatasetProvenance {
    }

    /** Dataset produced by a database query (reserved for later — fields TBD). */
    record DatabaseProvenance() implements DatasetProvenance {
    }

    /** Manually uploaded dataset (no source-specific detail today). */
    record UploadProvenance() implements DatasetProvenance {
    }
}
