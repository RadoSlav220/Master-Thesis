package com.thesis.orchestrator.exception;

/** Raised when an execution request is invalid (e.g. an unmapped expected measurement). */
public class InvalidExecutionException extends RuntimeException {
    public InvalidExecutionException(String message) {
        super(message);
    }
}
