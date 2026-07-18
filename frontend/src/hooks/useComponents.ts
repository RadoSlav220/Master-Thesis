import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { componentApi } from "../api/componentApi";
import type { CreateComponentRequest } from "../types";

const KEY = ["components"];

export function useComponents() {
  return useQuery({ queryKey: KEY, queryFn: componentApi.list });
}

export function useCreateComponent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateComponentRequest) => componentApi.create(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
