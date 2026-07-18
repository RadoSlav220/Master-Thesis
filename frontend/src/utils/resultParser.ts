import type { Execution, MapPoint } from "../types";

/** Fields that carry latitude, tolerating a few naming variants. */
const LAT_KEYS = ["latitude", "lat"];
const LNG_KEYS = ["longitude", "lng", "lon"];
/** Preferred metric fields, in priority order, before falling back to any number. */
const VALUE_KEYS = ["value", "pm25", "congestion"];

function pickNumber(row: Record<string, unknown>, keys: string[]): number | undefined {
  for (const key of keys) {
    const v = row[key];
    if (typeof v === "number" && Number.isFinite(v)) return v;
  }
  return undefined;
}

function pickValue(row: Record<string, unknown>): { value: number; label: string } | undefined {
  for (const key of VALUE_KEYS) {
    const v = row[key];
    if (typeof v === "number" && Number.isFinite(v)) return { value: v, label: key };
  }
  // Fallback: first numeric field that isn't a coordinate.
  for (const [key, v] of Object.entries(row)) {
    if (LAT_KEYS.includes(key) || LNG_KEYS.includes(key)) continue;
    if (typeof v === "number" && Number.isFinite(v)) return { value: v, label: key };
  }
  return undefined;
}

/**
 * Parses an execution's raw JSON `result` string into normalized map points.
 * Supports the mock components' shape:
 *   { "component": "...", "results": [ { latitude, longitude, pm25 | congestion } ] }
 * and the generic spec shape { results: [ { latitude, longitude, value } ] }.
 * Returns [] if the result is missing or unparseable.
 */
export function parseResultPoints(execution: Execution | null | undefined): MapPoint[] {
  if (!execution?.result) return [];
  let parsed: unknown;
  try {
    parsed = JSON.parse(execution.result);
  } catch {
    return [];
  }
  const results = (parsed as { results?: unknown })?.results;
  if (!Array.isArray(results)) return [];

  const points: MapPoint[] = [];
  for (const item of results) {
    if (typeof item !== "object" || item === null) continue;
    const row = item as Record<string, unknown>;
    const latitude = pickNumber(row, LAT_KEYS);
    const longitude = pickNumber(row, LNG_KEYS);
    const valued = pickValue(row);
    if (latitude === undefined || longitude === undefined || !valued) continue;
    points.push({ latitude, longitude, value: valued.value, label: valued.label });
  }
  return points;
}

/** Pretty-prints a raw JSON string; returns the original text if it can't parse. */
export function prettyJson(raw: string | null | undefined): string {
  if (!raw) return "";
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}
