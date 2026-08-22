"""Station-based CSV extraction.

Parses one or more uploaded CSV files into a relational shape: a deduplicated
list of measuring stations plus their measurements in long format (one row per
station / timestamp / measurement column). The caller supplies a column-role
mapping (per file: column name -> role) so this module knows which columns hold
the station id, coordinates, timestamp, extra station attributes, and the
measurement values.

Roles:
  STATION_ID, LATITUDE, LONGITUDE, TIMESTAMP, STATION_ATTRIBUTE, MEASUREMENT, IGNORE

The platform stays an orchestration layer: all CSV parsing/transformation lives
here in the Python service, not in the Spring backend.
"""

import math
from io import BytesIO, StringIO

import pandas as pd

from app.models.extraction_response import (
    ExtractionResponse,
    MeasurementRecord,
    StationRecord,
)
from app.services.dataset_analyzer import MalformedFileError

STATION_ID = "STATION_ID"
LATITUDE = "LATITUDE"
LONGITUDE = "LONGITUDE"
TIMESTAMP = "TIMESTAMP"
STATION_ATTRIBUTE = "STATION_ATTRIBUTE"
MEASUREMENT = "MEASUREMENT"
IGNORE = "IGNORE"

_VALID_ROLES = {
    STATION_ID,
    LATITUDE,
    LONGITUDE,
    TIMESTAMP,
    STATION_ATTRIBUTE,
    MEASUREMENT,
    IGNORE,
}
_REQUIRED_SINGLE_ROLES = (STATION_ID, LATITUDE, LONGITUDE)


class InvalidMappingError(ValueError):
    """Raised when the column-role mapping is missing or ambiguous."""


def _read_csv(filename: str, raw: bytes) -> pd.DataFrame:
    try:
        return pd.read_csv(StringIO(raw.decode("utf-8")))
    except UnicodeDecodeError:
        try:
            return pd.read_csv(BytesIO(raw))
        except Exception as exc:  # noqa: BLE001 - surfaced as 400
            raise MalformedFileError(f"Could not parse CSV {filename!r}: {exc}") from exc
    except Exception as exc:  # noqa: BLE001 - surfaced as 400
        raise MalformedFileError(f"Could not parse CSV {filename!r}: {exc}") from exc


def _validate_mapping(filename: str, columns: dict[str, str], headers: list[str]) -> None:
    for role in columns.values():
        if role not in _VALID_ROLES:
            raise InvalidMappingError(
                f"{filename!r}: unknown column role {role!r}."
            )
    for col in columns:
        if col not in headers:
            raise InvalidMappingError(
                f"{filename!r}: mapped column {col!r} is not present in the file."
            )
    for role in _REQUIRED_SINGLE_ROLES:
        count = sum(1 for r in columns.values() if r == role)
        if count == 0:
            raise InvalidMappingError(
                f"{filename!r}: exactly one {role} column is required (found none)."
            )
        if count > 1:
            raise InvalidMappingError(
                f"{filename!r}: exactly one {role} column is required (found {count})."
            )
    has_measurement = any(r == MEASUREMENT for r in columns.values())
    has_timestamp = any(r == TIMESTAMP for r in columns.values())
    if has_measurement and not has_timestamp:
        raise InvalidMappingError(
            f"{filename!r}: a TIMESTAMP column is required when the file has measurements."
        )


def _role_column(columns: dict[str, str], role: str) -> str | None:
    for col, r in columns.items():
        if r == role:
            return col
    return None


def _clean_str(value) -> str | None:
    if value is None:
        return None
    if isinstance(value, float) and math.isnan(value):
        return None
    text = str(value).strip()
    return text or None


def _to_float(value) -> float | None:
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    if math.isnan(result):
        return None
    return result


def extract(
    files: list[tuple[str, bytes]], mapping: dict[str, dict[str, str]]
) -> ExtractionResponse:
    """Extracts stations + measurements from the uploaded files.

    ``files`` is a list of (filename, raw_bytes). ``mapping`` maps each filename
    to a {column -> role} dict. Stations are deduplicated by their external id
    across all files (first-seen coordinates win; conflicts are reported as
    warnings). Measurements are emitted in long format.
    """
    if not files:
        raise InvalidMappingError("At least one file is required.")

    stations: dict[str, StationRecord] = {}
    measurements: list[MeasurementRecord] = []
    warnings: list[str] = []

    for filename, raw in files:
        columns = mapping.get(filename)
        if columns is None:
            raise InvalidMappingError(f"No column mapping provided for file {filename!r}.")

        df = _read_csv(filename, raw)
        headers = [str(c) for c in df.columns]
        _validate_mapping(filename, columns, headers)

        id_col = _role_column(columns, STATION_ID)
        lat_col = _role_column(columns, LATITUDE)
        lon_col = _role_column(columns, LONGITUDE)
        ts_col = _role_column(columns, TIMESTAMP)
        attr_cols = [c for c, r in columns.items() if r == STATION_ATTRIBUTE]
        measurement_cols = [c for c, r in columns.items() if r == MEASUREMENT]

        parsed_ts = None
        if ts_col is not None:
            parsed_ts = pd.to_datetime(df[ts_col], errors="coerce", utc=True)

        for idx, row in df.iterrows():
            station_id = _clean_str(row[id_col])
            if station_id is None:
                continue

            existing = stations.get(station_id)
            lat = _to_float(row[lat_col])
            lon = _to_float(row[lon_col])
            attributes = {
                col: _clean_str(row[col])
                for col in attr_cols
                if _clean_str(row[col]) is not None
            }
            if existing is None:
                stations[station_id] = StationRecord(
                    stationExternalId=station_id,
                    latitude=lat,
                    longitude=lon,
                    attributes=attributes,
                )
            else:
                if (
                    lat is not None
                    and lon is not None
                    and existing.latitude is not None
                    and existing.longitude is not None
                    and (lat != existing.latitude or lon != existing.longitude)
                ):
                    warnings.append(
                        f"Station {station_id!r} has conflicting coordinates across rows; "
                        f"keeping the first-seen ({existing.latitude}, {existing.longitude})."
                    )
                for key, val in attributes.items():
                    existing.attributes.setdefault(key, val)

            timestamp = None
            if parsed_ts is not None:
                ts_value = parsed_ts.iloc[df.index.get_loc(idx)]
                if not pd.isna(ts_value):
                    timestamp = ts_value.isoformat()

            for col in measurement_cols:
                value = _clean_str(row[col])
                if value is None:
                    continue
                measurements.append(
                    MeasurementRecord(
                        stationExternalId=station_id,
                        timestamp=timestamp,
                        measurementType=col,
                        value=value,
                        valueNumeric=_to_float(row[col]),
                    )
                )

    return ExtractionResponse(
        stations=list(stations.values()),
        measurements=measurements,
        warnings=warnings,
    )
