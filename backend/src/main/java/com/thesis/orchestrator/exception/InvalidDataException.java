package com.thesis.orchestrator.exception;

/**
 * Raised when the analysis service rejects a dataset's content as malformed
 * (an HTTP 4xx from the service — bad data, not an outage). Distinct from
 * {@link DatasetAnalysisException}, which signals a transient service failure.
 */
public class InvalidDataException extends RuntimeException {
    public InvalidDataException(String message) {
        super(message);
    }
}
