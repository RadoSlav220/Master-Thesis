import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { datasetApi } from "../api/datasetApi";
import type { CreateDatasetRequest } from "../types";

const KEY = ["datasets"];

export function useDatasets() {
  return useQuery({ queryKey: KEY, queryFn: datasetApi.list });
}

export function useDataset(id: string | null) {
  return useQuery({
    queryKey: [...KEY, id],
    queryFn: () => datasetApi.get(id as string),
    enabled: !!id,
  });
}

export function useDatasetGeoJson(id: string | null) {
  return useQuery({
    queryKey: [...KEY, id, "geojson"],
    queryFn: () => datasetApi.getGeoJson(id as string),
    enabled: !!id,
  });
}

export function useCreateDataset() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateDatasetRequest) => datasetApi.create(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useUploadDataset() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { file: File; name: string; description?: string }) =>
      datasetApi.upload(vars.file, vars.name, vars.description),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useAnalyzeDataset() {
  return useMutation({
    mutationFn: (id: string) => datasetApi.analyze(id),
  });
}

export function useDeleteDataset() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => datasetApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
