export type ExecutionStatus = "CREATED" | "RUNNING" | "COMPLETED" | "FAILED";

export type DatasetType = "CSV" | "GEOJSON";

export interface Dataset {
  id: string;
  name: string;
  type: string;
  description: string | null;
  hasGeoJson: boolean;
  datasetType: string | null;
  analysisResult: string | null;
  sourceId: string | null;
  createdAt: string;
}

export interface CreateDatasetRequest {
  name: string;
  type: DatasetType;
  description?: string;
}

export interface FetchDatasetRequest {
  name: string;
  startDate: string;
  endDate: string;
}

export interface Component {
  id: string;
  name: string;
  endpointUrl: string;
  inputSchema: string | null;
  outputSchema: string | null;
  description: string | null;
}

export interface CreateComponentRequest {
  name: string;
  endpointUrl: string;
  description?: string;
}

export interface Execution {
  id: string;
  datasetId: string;
  componentId: string;
  status: ExecutionStatus;
  result: string | null;
  errorMessage: string | null;
  filterSpec: string | null;
  createdAt: string;
  finishedAt: string | null;
}

export interface FilterSpec {
  columns: string[];
  limit?: number;
}

export interface CreateExecutionRequest {
  datasetId: string;
  componentId: string;
  filter?: FilterSpec;
}

/** Structural analysis of a dataset returned by the analysis service (via backend). */
export interface DatasetAnalysis {
  datasetType: string;
  columns?: string[];
  properties?: string[];
}

export type DataSourceType = "API";

export type DataSourceOutputFormat = "CSV" | "GEOJSON";

export interface DataSource {
  id: string;
  name: string;
  type: string;
  outputFormat: string;
  description: string | null;
  createdAt: string;
}

export interface CreateDataSourceRequest {
  name: string;
  type: DataSourceType;
  outputFormat: DataSourceOutputFormat;
  description?: string;
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
