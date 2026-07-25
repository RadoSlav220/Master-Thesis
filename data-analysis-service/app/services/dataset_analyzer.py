"""Dataset structure analysis.

This first version only inspects a dataset file and reports its structure:
column names for CSV, and the union of feature property keys for GeoJSON.
No filtering or transformation is performed.
"""

import json
from io import BytesIO, StringIO

import pandas as pd

from app.models.analysis_response import AnalysisResponse


class UnsupportedFileError(ValueError):
    """Raised when the file type is not supported."""


class MalformedFileError(ValueError):
    """Raised when a supported file cannot be parsed."""


def analyze_csv(raw: bytes) -> AnalysisResponse:
    try:
        df = pd.read_csv(StringIO(raw.decode("utf-8")))
    except UnicodeDecodeError:
        # Fall back to the raw bytes for non-UTF-8 encodings.
        try:
            df = pd.read_csv(BytesIO(raw))
        except Exception as exc:  # noqa: BLE001 - surfaced as 400
            raise MalformedFileError(f"Could not parse CSV: {exc}") from exc
    except Exception as exc:  # noqa: BLE001 - surfaced as 400
        raise MalformedFileError(f"Could not parse CSV: {exc}") from exc

    return AnalysisResponse(datasetType="CSV", columns=[str(c) for c in df.columns])


def analyze_geojson(raw: bytes) -> AnalysisResponse:
    try:
        data = json.loads(raw.decode("utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise MalformedFileError(f"Could not parse GeoJSON: {exc}") from exc

    if not isinstance(data, dict) or data.get("type") != "FeatureCollection":
        raise MalformedFileError('GeoJSON must be a "FeatureCollection".')

    features = data.get("features")
    if not isinstance(features, list):
        raise MalformedFileError('GeoJSON FeatureCollection must contain a "features" array.')

    # Union of property keys, preserving first-seen order across features.
    keys: list[str] = []
    for feature in features:
        if not isinstance(feature, dict):
            continue
        props = feature.get("properties")
        if isinstance(props, dict):
            for key in props:
                if key not in keys:
                    keys.append(key)

    return AnalysisResponse(datasetType="GEOJSON", properties=keys)


def analyze(filename: str, raw: bytes) -> AnalysisResponse:
    """Dispatches to the right analyzer based on the file extension."""
    name = (filename or "").lower()
    if name.endswith(".csv"):
        return analyze_csv(raw)
    if name.endswith(".geojson") or name.endswith(".json"):
        return analyze_geojson(raw)
    raise UnsupportedFileError(
        f"Unsupported file type: {filename!r}. Expected .csv, .geojson, or .json."
    )
