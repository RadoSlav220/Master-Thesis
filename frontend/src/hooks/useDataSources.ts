import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { dataSourceApi } from "../api/dataSourceApi";
import type { CreateDataSourceRequest, FetchDatasetRequest } from "../types";

const KEY = ["data-sources"];

export function useDataSources() {
  return useQuery({ queryKey: KEY, queryFn: dataSourceApi.list });
}

export function useCreateDataSource() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateDataSourceRequest) => dataSourceApi.create(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useFetchDataset() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; payload: FetchDatasetRequest }) =>
      dataSourceApi.fetch(vars.id, vars.payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["datasets"] }),
  });
}
