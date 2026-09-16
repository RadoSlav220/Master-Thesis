"""Dataset structure analysis.

This first version only inspects a dataset file and reports its structure:
the column names for a CSV. No filtering or transformation is performed.
"""

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


def analyze(filename: str, raw: bytes) -> AnalysisResponse:
    """Dispatches to the right analyzer based on the file extension."""
    name = (filename or "").lower()
    if name.endswith(".csv"):
        return analyze_csv(raw)
    raise UnsupportedFileError(
        f"Unsupported file type: {filename!r}. Expected .csv."
    )
