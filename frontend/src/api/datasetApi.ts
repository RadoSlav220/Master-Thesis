import { apiClient } from "./client";
import type { CreateDatasetRequest, Dataset, DatasetAnalysis, GeoJsonFeatureCollection } from "../types";

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
  getGeoJson: async (id: string): Promise<GeoJsonFeatureCollection> => {
    const { data } = await apiClient.get<GeoJsonFeatureCollection>(`/datasets/${id}/geojson`);
    return data;
  },
  analyze: async (id: string): Promise<DatasetAnalysis> => {
    const { data } = await apiClient.post<DatasetAnalysis>(`/datasets/${id}/analyze`);
    return data;
  },
};
