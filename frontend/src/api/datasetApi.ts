import { apiClient } from "./client";
import type {
  CreateDatasetRequest,
  Dataset,
  DatasetAnalysis,
  DatasetStats,
  GeoJsonFeatureCollection,
  Measurement,
  Station,
  StationMeasurementRenames,
  StationUploadMapping,
  UpdateDatasetRequest,
} from "../types";

export const datasetApi = {
  list: async (): Promise<Dataset[]> => {
    const { data } = await apiClient.get<Dataset[]>("/datasets");
    return data;
  },
  get: async (id: string): Promise<Dataset> => {
    const { data } = await apiClient.get<Dataset>(`/datasets/${id}`);
    return data;
  },
  create: async (payload: CreateDatasetRequest): Promise<Dataset> => {
    const { data } = await apiClient.post<Dataset>("/datasets", payload);
    return data;
  },
  upload: async (file: File, name: string, description?: string): Promise<Dataset> => {
    const form = new FormData();
    form.append("file", file);
    form.append("name", name);
    if (description) form.append("description", description);
    const { data } = await apiClient.post<Dataset>("/datasets/upload", form, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    return data;
  },
  uploadStations: async (
    files: File[],
    name: string,
    mapping: StationUploadMapping,
    description?: string,
    renames?: StationMeasurementRenames,
  ): Promise<Dataset> => {
    const form = new FormData();
    files.forEach((f) => form.append("files", f));
    form.append("name", name);
    form.append("mapping", JSON.stringify(mapping));
    if (description) form.append("description", description);
    if (renames && Object.keys(renames).length > 0) {
      form.append("renames", JSON.stringify(renames));
    }
    const { data } = await apiClient.post<Dataset>("/datasets/upload-stations", form, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    return data;
  },
  getStations: async (id: string): Promise<Station[]> => {
    const { data } = await apiClient.get<Station[]>(`/datasets/${id}/stations`);
    return data;
  },
  getMeasurements: async (id: string, limit?: number): Promise<Measurement[]> => {
    const { data } = await apiClient.get<Measurement[]>(`/datasets/${id}/measurements`, {
      params: limit ? { limit } : undefined,
    });
    return data;
  },
  getStats: async (id: string): Promise<DatasetStats> => {
    const { data } = await apiClient.get<DatasetStats>(`/datasets/${id}/stats`);
    return data;
  },
  getGeoJson: async (id: string): Promise<GeoJsonFeatureCollection> => {
    const { data } = await apiClient.get<GeoJsonFeatureCollection>(`/datasets/${id}/geojson`);
    return data;
  },
  download: async (id: string): Promise<{ blob: Blob; filename: string }> => {
    const response = await apiClient.get(`/datasets/${id}/download`, { responseType: "blob" });
    const disposition = response.headers["content-disposition"] as string | undefined;
    const match = disposition?.match(/filename\*?=(?:UTF-8'')?"?([^"';]+)"?/i);
    const filename = match ? decodeURIComponent(match[1]) : `dataset-${id}`;
    return { blob: response.data as Blob, filename };
  },
  analyze: async (id: string): Promise<DatasetAnalysis> => {
    const { data } = await apiClient.post<DatasetAnalysis>(`/datasets/${id}/analyze`);
    return data;
  },
  update: async (id: string, payload: UpdateDatasetRequest): Promise<Dataset> => {
    const { data } = await apiClient.put<Dataset>(`/datasets/${id}`, payload);
    return data;
  },
  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/datasets/${id}`);
  },
};
