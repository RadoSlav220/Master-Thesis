"""FastAPI entrypoint for the Dataset Analysis Service.

A specialized data-processing component of the Local Digital Twin platform. It
inspects a dataset file's structure (/analyze) and reduces it to selected
columns/properties (/filter). Orchestration stays in the Spring Boot backend.
"""

import json

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from app.models.analysis_response import AnalysisResponse
from app.models.extraction_response import ExtractionResponse
from app.models.filter_response import FilterResponse
from app.services import dataset_analyzer, dataset_filter, station_extractor
from app.services.dataset_analyzer import UnsupportedFileError

app = FastAPI(title="Dataset Analysis Service", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/analyze", response_model=AnalysisResponse, response_model_exclude_none=True)
async def analyze(file: UploadFile = File(...)) -> AnalysisResponse:
    raw = await file.read()
    try:
        return dataset_analyzer.analyze(file.filename or "", raw)
    except UnsupportedFileError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except ValueError as exc:
        # Malformed CSV / GeoJSON.
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/filter", response_model=FilterResponse)
async def filter_endpoint(
    file: UploadFile = File(...),
    columns: str = Form(""),
    limit: int | None = Form(None),
) -> FilterResponse:
    raw = await file.read()
    selected = [c.strip() for c in columns.split(",") if c.strip()]
    try:
        dataset_type, content = dataset_filter.filter_dataset(
            file.filename or "", raw, selected, limit
        )
    except UnsupportedFileError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return FilterResponse(datasetType=dataset_type, content=content)


@app.post("/extract-stations", response_model=ExtractionResponse)
async def extract_stations(
    files: list[UploadFile] = File(...),
    mapping: str = Form(...),
) -> ExtractionResponse:
    """Parses station CSV files into relational stations + long-format measurements.

    ``mapping`` is a JSON object keyed by filename: {"<file>": {"<col>": "<ROLE>"}}.
    """
    try:
        parsed_mapping = json.loads(mapping)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=400, detail=f"Invalid mapping JSON: {exc}") from exc
    if not isinstance(parsed_mapping, dict):
        raise HTTPException(
            status_code=400, detail="mapping must be a JSON object keyed by filename."
        )

    payload: list[tuple[str, bytes]] = []
    for upload in files:
        raw = await upload.read()
        payload.append((upload.filename or "", raw))

    try:
        return station_extractor.extract(payload, parsed_mapping)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
