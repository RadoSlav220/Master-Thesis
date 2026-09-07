package com.thesis.orchestrator.dto;

/** A dataset packaged for file download (content bytes + filename + content type). */
public record DatasetDownload(byte[] content, String filename, String contentType) {
}
