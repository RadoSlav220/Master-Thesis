package com.thesis.orchestrator.exception;

/**
 * Raised when fetching a snapshot from an external data source fails (network error or
 * non-2xx response). Surfaced as a 502 so the caller knows the upstream source, not the
 * orchestrator, is at fault.
 */
public class DataSourceFetchException extends RuntimeException {
    public DataSourceFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
