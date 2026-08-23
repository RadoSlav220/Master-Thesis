import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { datasetApi } from "../api/datasetApi";
import type {
  CreateDatasetRequest,
  StationUploadMapping,
  UpdateDatasetRequest,
} from "../types";

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

export function useUploadStations() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      files: File[];
      name: string;
      mapping: StationUploadMapping;
      description?: string;
    }) => datasetApi.uploadStations(vars.files, vars.name, vars.mapping, vars.description),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useDatasetStations(id: string | null) {
  return useQuery({
    queryKey: [...KEY, id, "stations"],
    queryFn: () => datasetApi.getStations(id as string),
    enabled: !!id,
  });
}

export function useDatasetMeasurements(id: string | null, limit?: number) {
  return useQuery({
    queryKey: [...KEY, id, "measurements", limit],
    queryFn: () => datasetApi.getMeasurements(id as string, limit),
    enabled: !!id,
  });
}

export function useDatasetStats(id: string | null) {
  return useQuery({
    queryKey: [...KEY, id, "stats"],
    queryFn: () => datasetApi.getStats(id as string),
    enabled: !!id,
  });
}

export function useAnalyzeDataset() {
  return useMutation({
    mutationFn: (id: string) => datasetApi.analyze(id),
  });
}

export function useDownloadDataset() {
  return useMutation({
    mutationFn: async (id: string) => {
      const { blob, filename } = await datasetApi.download(id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    },
  });
}

export function useDeleteDataset() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => datasetApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useUpdateDataset() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; payload: UpdateDatasetRequest }) =>
      datasetApi.update(vars.id, vars.payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
