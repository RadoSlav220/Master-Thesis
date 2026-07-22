import type { Execution, GeoJsonFeatureCollection } from "../types";

/** Property names, in priority order, treated as the primary metric to visualize. */
const VALUE_KEYS = ["value", "pm25", "congestion"];

/** Type guard for a GeoJSON FeatureCollection. */
function isFeatureCollection(v: unknown): v is GeoJsonFeatureCollection {
  return (
    typeof v === "object" &&
    v !== null &&
    (v as { type?: unknown }).type === "FeatureCollection" &&
    Array.isArray((v as { features?: unknown }).features)
  );
}

/**
 * Parses an execution's raw JSON `result` string into a GeoJSON FeatureCollection.
 * Returns null if the result is missing, unparseable, or not a FeatureCollection.
 */
export function parseFeatureCollection(
  execution: Execution | null | undefined,
): GeoJsonFeatureCollection | null {
  if (!execution?.result) return null;
  try {
    const parsed: unknown = JSON.parse(execution.result);
    return isFeatureCollection(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

/** Distinct geometry types present in a FeatureCollection, in first-seen order. */
export function geometryTypes(fc: GeoJsonFeatureCollection | null | undefined): string[] {
  if (!fc) return [];
  const seen: string[] = [];
  for (const f of fc.features) {
    const t = f.geometry?.type;
    if (t && !seen.includes(t)) seen.push(t);
  }
  return seen;
}

/**
 * Detects the primary numeric property to visualize: the first of value/pm25/
 * congestion that appears, else the first numeric property found on any feature.
 */
export function detectValueProperty(fc: GeoJsonFeatureCollection | null | undefined): string | null {
  if (!fc) return null;
  for (const key of VALUE_KEYS) {
    if (fc.features.some((f) => typeof f.properties?.[key] === "number")) return key;
  }
  for (const f of fc.features) {
    for (const [key, v] of Object.entries(f.properties ?? {})) {
      if (typeof v === "number" && Number.isFinite(v)) return key;
    }
  }
  return null;
}

/** Collects the numeric values of a given property across all features. */
export function collectValues(
  fc: GeoJsonFeatureCollection | null | undefined,
  property: string,
): number[] {
  if (!fc) return [];
  const out: number[] = [];
  for (const f of fc.features) {
    const v = f.properties?.[property];
    if (typeof v === "number" && Number.isFinite(v)) out.push(v);
  }
  return out;
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
