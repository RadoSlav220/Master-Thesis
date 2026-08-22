import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { executionApi } from "../api/executionApi";
import type { CreateExecutionRequest } from "../types";

const KEY = ["executions"];

export function useExecutions() {
  return useQuery({ queryKey: KEY, queryFn: executionApi.list });
}

export function useExecution(id: string | null) {
  return useQuery({
    queryKey: [...KEY, id],
    queryFn: () => executionApi.get(id as string),
    enabled: !!id,
  });
}

export function useCreateExecution() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateExecutionRequest) => executionApi.create(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useDeleteExecution() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => executionApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useDownloadExecution() {
  return useMutation({
    mutationFn: async (id: string) => {
      const { blob, filename } = await executionApi.download(id);
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
