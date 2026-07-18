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
  create: async (payload: CreateExecutionRequest): Promise<Execution> => {
    const { data } = await apiClient.post<Execution>("/executions", payload);
    return data;
  },
};
