import type { Dataset } from "../types";

/**
 * A station-based dataset stores its data relationally (stations + measurements) rather
 * than in a single content blob: it originates from an UPLOAD, has no GeoJSON content, and
 * carries no persisted structure analysis (only content-based uploads run /analyze).
 */
export function isStationDataset(dataset: Pick<Dataset, "datasetOrigin" | "hasGeoJson" | "analysisResult">): boolean {
  return (
    dataset.datasetOrigin === "UPLOAD" && !dataset.hasGeoJson && !dataset.analysisResult
  );
}

/**
 * A dataset is downloadable when it has raw content (CSV/GeoJSON, single file) or is a
 * station-based dataset (reconstructed as a zip of stations.csv + measurements.csv).
 */
export function isDownloadable(
  dataset: Pick<Dataset, "hasContent" | "datasetOrigin" | "hasGeoJson" | "analysisResult">,
): boolean {
  return dataset.hasContent || isStationDataset(dataset);
}
