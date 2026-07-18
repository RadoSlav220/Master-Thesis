export type ExecutionStatus = "CREATED" | "RUNNING" | "COMPLETED" | "FAILED";

export type DatasetType = "CSV" | "GEOJSON";

export interface Dataset {
  id: string;
  name: string;
  type: string;
  description: string | null;
  createdAt: string;
}

export interface CreateDatasetRequest {
  name: string;
  type: DatasetType;
  description?: string;
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
  createdAt: string;
  finishedAt: string | null;
}

export interface CreateExecutionRequest {
  datasetId: string;
  componentId: string;
}

/** Normalized geospatial point derived from an execution result. */
export interface MapPoint {
  latitude: number;
  longitude: number;
  value: number;
  label: string;
}
