import type { Dataset } from "../types";

/**
 * A station-based dataset stores its data relationally (stations + measurements) rather
 * than in a single content blob: it originates from an UPLOAD, has no stored content, and
 * carries no persisted structure analysis (only content-based uploads run /analyze).
 */
export function isStationDataset(dataset: Pick<Dataset, "datasetOrigin" | "hasContent" | "analysisResult">): boolean {
  return (
    dataset.datasetOrigin === "UPLOAD" && !dataset.hasContent && !dataset.analysisResult
  );
}

/**
 * A dataset is downloadable when it has raw CSV content (single file) or is a
 * station-based dataset (reconstructed as a zip of stations.csv + measurements.csv).
 */
export function isDownloadable(
  dataset: Pick<Dataset, "hasContent" | "datasetOrigin" | "analysisResult">,
): boolean {
  return dataset.hasContent || isStationDataset(dataset);
}
