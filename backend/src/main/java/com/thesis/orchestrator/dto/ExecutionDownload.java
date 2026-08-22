package com.thesis.orchestrator.dto;

/** An execution's result packaged for file download (content + filename + content type). */
public record ExecutionDownload(String content, String filename, String contentType) {
}
