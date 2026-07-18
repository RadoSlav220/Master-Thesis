import { apiClient } from "./client";
import type { CreateDatasetRequest, Dataset } from "../types";

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
};
