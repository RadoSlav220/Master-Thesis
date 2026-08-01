package com.thesis.orchestrator.domain;

/**
 * Where a dataset came from. Drives which origin-specific {@code provenance} shape is
 * stored/returned for the dataset.
 */
public enum DatasetOrigin {
    /** Manually uploaded file. */
    UPLOAD,
    /** Fetched from a data source's HTTP API. */
    API,
    /** Produced by a database query (reserved for later). */
    DATABASE
}
