"""Dataset filtering.

Reduces a dataset to a subset of its columns/properties and optionally caps the
number of rows/features. This is the second data-processing capability of the
service (after structure analysis). No value predicates or aggregations yet.
"""

import json
from io import StringIO

import pandas as pd

from app.services.dataset_analyzer import MalformedFileError, UnsupportedFileError


def filter_csv(raw: bytes, columns: list[str], limit: int | None) -> str:
    try:
        df = pd.read_csv(StringIO(raw.decode("utf-8")))
    except Exception as exc:  # noqa: BLE001 - surfaced as 400
        raise MalformedFileError(f"Could not parse CSV: {exc}") from exc

    if columns:
        keep = [c for c in columns if c in df.columns]
        df = df[keep]
    if limit is not None:
        df = df.head(limit)
    return df.to_csv(index=False)


def filter_geojson(raw: bytes, columns: list[str], limit: int | None) -> str:
    try:
        data = json.loads(raw.decode("utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise MalformedFileError(f"Could not parse GeoJSON: {exc}") from exc

    if not isinstance(data, dict) or data.get("type") != "FeatureCollection":
        raise MalformedFileError('GeoJSON must be a "FeatureCollection".')

    features = data.get("features")
    if not isinstance(features, list):
        raise MalformedFileError('GeoJSON FeatureCollection must contain a "features" array.')

    if limit is not None:
        features = features[:limit]

    if columns:
        selected = set(columns)
        for feature in features:
            if isinstance(feature, dict) and isinstance(feature.get("properties"), dict):
                feature["properties"] = {
                    k: v for k, v in feature["properties"].items() if k in selected
                }

    return json.dumps({"type": "FeatureCollection", "features": features})


def filter_dataset(filename: str, raw: bytes, columns: list[str], limit: int | None):
    """Dispatches to the right filter and returns (datasetType, content)."""
    name = (filename or "").lower()
    if name.endswith(".csv"):
        return "CSV", filter_csv(raw, columns, limit)
    if name.endswith(".geojson") or name.endswith(".json"):
        return "GEOJSON", filter_geojson(raw, columns, limit)
    raise UnsupportedFileError(
        f"Unsupported file type: {filename!r}. Expected .csv, .geojson, or .json."
    )
