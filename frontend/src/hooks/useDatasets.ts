import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { datasetApi } from "../api/datasetApi";
import type { CreateDatasetRequest } from "../types";

const KEY = ["datasets"];

export function useDatasets() {
  return useQuery({ queryKey: KEY, queryFn: datasetApi.list });
}

export function useCreateDataset() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateDatasetRequest) => datasetApi.create(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
