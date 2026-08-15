package com.thesis.orchestrator.dto;

/** A dataset's raw content packaged for file download (content + filename + content type). */
public record DatasetDownload(String content, String filename, String contentType) {
}
