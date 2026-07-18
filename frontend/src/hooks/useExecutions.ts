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
