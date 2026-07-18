package com.thesis.orchestrator.exception;

public class ComponentInvocationException extends RuntimeException {
    public ComponentInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
