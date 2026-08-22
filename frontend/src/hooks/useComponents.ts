import { useQuery } from "@tanstack/react-query";
import { componentApi } from "../api/componentApi";

const KEY = ["components"];

export function useComponents() {
  return useQuery({ queryKey: KEY, queryFn: componentApi.list });
}
