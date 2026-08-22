from typing import Optional

from pydantic import BaseModel


class StationRecord(BaseModel):
    """A single measuring station, deduplicated across the uploaded files."""

    stationExternalId: str
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    attributes: dict[str, str] = {}


class MeasurementRecord(BaseModel):
    """A single reading in long format: one row per (station, timestamp, type)."""

    stationExternalId: str
    timestamp: Optional[str] = None
    measurementType: str
    value: Optional[str] = None
    valueNumeric: Optional[float] = None


class ExtractionResponse(BaseModel):
    """The relational shape extracted from one or more station CSV files.

    ``stations`` is deduplicated by ``stationExternalId``; ``measurements`` is the
    long-format expansion (one row per station/timestamp/measurement column).
    ``warnings`` collects non-fatal issues (e.g. conflicting coordinates).
    """

    stations: list[StationRecord]
    measurements: list[MeasurementRecord]
    warnings: list[str] = []
