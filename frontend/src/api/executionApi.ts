import { apiClient } from "./client";
import type { CreateExecutionRequest, Execution } from "../types";

export const executionApi = {
  list: async (): Promise<Execution[]> => {
    const { data } = await apiClient.get<Execution[]>("/executions");
    return data;
  },
  get: async (id: string): Promise<Execution> => {
    const { data } = await apiClient.get<Execution>(`/executions/${id}`);
    return data;
  },
  download: async (id: string): Promise<{ blob: Blob; filename: string }> => {
    const response = await apiClient.get(`/executions/${id}/download`, { responseType: "blob" });
    const disposition = response.headers["content-disposition"] as string | undefined;
    const match = disposition?.match(/filename\*?=(?:UTF-8'')?"?([^"';]+)"?/i);
    const filename = match ? decodeURIComponent(match[1]) : `execution-${id}`;
    return { blob: response.data as Blob, filename };
  },
  create: async (payload: CreateExecutionRequest): Promise<Execution> => {
    const { data } = await apiClient.post<Execution>("/executions", payload);
    return data;
  },
  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/executions/${id}`);
  },
};
