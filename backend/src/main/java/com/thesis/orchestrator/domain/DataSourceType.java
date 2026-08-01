package com.thesis.orchestrator.domain;

/**
 * The kind of external data source. Drives how the source is fetched and which
 * type-specific configuration it carries.
 */
public enum DataSourceType {
    /** An HTTP API queried with query parameters. */
    API,
    /** A database queried with SQL (reserved for later). */
    DATABASE
}
