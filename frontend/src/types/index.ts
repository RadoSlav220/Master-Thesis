export type ExecutionStatus = "CREATED" | "RUNNING" | "COMPLETED" | "FAILED";

export type DatasetType = "CSV";

export type DatasetOrigin = "UPLOAD";

/** Origin-specific provenance, discriminated by `type` (mirrors the backend). */
export type DatasetProvenance = { type: "UPLOAD" };

export interface Dataset {
  id: string;
  name: string;
  type: string;
  description: string | null;
  datasetOrigin: DatasetOrigin;
  provenance: DatasetProvenance | null;
  createdAt: string;
}

export interface CreateDatasetRequest {
  name: string;
  type: DatasetType;
  description?: string;
}

export interface UpdateDatasetRequest {
  name: string;
  description?: string;
}

/** A column's assigned role in a station-based CSV upload. */
export type ColumnRole =
  | "STATION_ID"
  | "LATITUDE"
  | "LONGITUDE"
  | "TIMESTAMP"
  | "STATION_ATTRIBUTE"
  | "MEASUREMENT"
  | "IGNORE";

/** Per-file column-role mapping sent with a station upload: filename -> (column -> role). */
export type StationUploadMapping = Record<string, Record<string, ColumnRole>>;

/**
 * Optional per-file canonical names for MEASUREMENT columns, sent with a station
 * upload: filename -> (column -> canonical measurement name). Columns renamed to the
 * same name are unified into one measurement type.
 */
export type StationMeasurementRenames = Record<string, Record<string, string>>;

export interface Station {
  id: string;
  stationExternalId: string;
  latitude: number | null;
  longitude: number | null;
  attributes: string | null;
}

export interface Measurement {
  id: string;
  stationExternalId: string;
  timestamp: string | null;
  measurementType: string;
  value: string | null;
  valueNumeric: number | null;
}

export interface DatasetStats {
  stationCount: number;
  measurementCount: number;
}

export interface Component {
  id: string;
  name: string;
  endpointUrl: string;
  inputSchema: string | null;
  outputSchema: string | null;
  description: string | null;
  expectedMeasurements: string[];
}

export interface Execution {
  id: string;
  datasetId: string;
  componentId: string;
  status: ExecutionStatus;
  result: string | null;
  errorMessage: string | null;
  measurementMapping: string | null;
  createdAt: string;
  finishedAt: string | null;
}

export interface CreateExecutionRequest {
  datasetId: string;
  componentId: string;
  /** Component expected-measurement name -> dataset measurement column. */
  measurementMapping?: Record<string, string>;
}

/** Minimal GeoJSON types (subset of the spec we render). */
export type GeoJsonGeometryType =
  | "Point"
  | "MultiPoint"
  | "LineString"
  | "MultiLineString"
  | "Polygon"
  | "MultiPolygon";

export interface GeoJsonGeometry {
  type: GeoJsonGeometryType;
  coordinates: unknown;
}

export interface GeoJsonFeature {
  type: "Feature";
  properties: Record<string, unknown> | null;
  geometry: GeoJsonGeometry | null;
}

export interface GeoJsonFeatureCollection {
  type: "FeatureCollection";
  features: GeoJsonFeature[];
}
