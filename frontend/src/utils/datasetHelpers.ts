import type { Dataset } from "../types";

/**
 * Every dataset is station-based: it originates from an UPLOAD and stores its data
 * relationally in GraFlex (stations + measurements), read back over HTTP.
 */
export function isStationDataset(dataset: Pick<Dataset, "datasetOrigin">): boolean {
  return dataset.datasetOrigin === "UPLOAD";
}

/**
 * A station-based dataset is downloadable as a zip of stations.csv + measurements.csv
 * (reconstructed from GraFlex).
 */
export function isDownloadable(dataset: Pick<Dataset, "datasetOrigin">): boolean {
  return isStationDataset(dataset);
}
