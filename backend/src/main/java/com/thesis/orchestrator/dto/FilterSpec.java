package com.thesis.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * Optional filter applied to a dataset before it is passed to a component/model.
 * v1 supports column/property selection and a row/feature limit. Both fields are
 * optional; empty {@code columns} keeps all, null {@code limit} applies no cap.
 */
public record FilterSpec(
        List<String> columns,
        Integer limit
) {
    @JsonIgnore
    public boolean isEmpty() {
        return (columns == null || columns.isEmpty()) && limit == null;
    }
}
