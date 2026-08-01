import { apiClient } from "./client";
import type {
  CreateDataSourceRequest,
  DataSource,
  Dataset,
  FetchDatasetRequest,
} from "../types";

export const dataSourceApi = {
  list: async (): Promise<DataSource[]> => {
    const { data } = await apiClient.get<DataSource[]>("/data-sources");
    return data;
  },
  get: async (id: string): Promise<DataSource> => {
    const { data } = await apiClient.get<DataSource>(`/data-sources/${id}`);
    return data;
  },
  create: async (payload: CreateDataSourceRequest): Promise<DataSource> => {
    const { data } = await apiClient.post<DataSource>("/data-sources", payload);
    return data;
  },
  fetch: async (id: string, payload: FetchDatasetRequest): Promise<Dataset> => {
    const { data } = await apiClient.post<Dataset>(`/data-sources/${id}/fetch`, payload);
    return data;
  },
  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/data-sources/${id}`);
  },
};
