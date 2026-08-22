import { apiClient } from "./client";
import type { Component } from "../types";

export const componentApi = {
  list: async (): Promise<Component[]> => {
    const { data } = await apiClient.get<Component[]>("/components");
    return data;
  },
  get: async (id: string): Promise<Component> => {
    const { data } = await apiClient.get<Component>(`/components/${id}`);
    return data;
  },
};
