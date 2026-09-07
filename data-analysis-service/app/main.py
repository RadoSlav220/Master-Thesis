"""FastAPI entrypoint for the Dataset Analysis Service.

A specialized data-processing component of the Local Digital Twin platform. It
inspects a dataset file's structure (/analyze) and extracts stations +
measurements from station CSV uploads (/extract-stations). Orchestration stays
in the Spring Boot backend.
"""

import json

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from app.models.analysis_response import AnalysisResponse
from app.models.extraction_response import ExtractionResponse
from app.services import dataset_analyzer, station_extractor
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


@app.post("/extract-stations", response_model=ExtractionResponse)
async def extract_stations(
    files: list[UploadFile] = File(...),
    mapping: str = Form(...),
    renames: str = Form(""),
) -> ExtractionResponse:
    """Parses station CSV files into relational stations + long-format measurements.

    ``mapping`` is a JSON object keyed by filename: {"<file>": {"<col>": "<ROLE>"}}.
    ``renames`` is an optional JSON object keyed by filename mapping MEASUREMENT
    columns to a canonical name: {"<file>": {"<col>": "<name>"}}.
    """
    try:
        parsed_mapping = json.loads(mapping)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=400, detail=f"Invalid mapping JSON: {exc}") from exc
    if not isinstance(parsed_mapping, dict):
        raise HTTPException(
            status_code=400, detail="mapping must be a JSON object keyed by filename."
        )

    parsed_renames: dict = {}
    if renames.strip():
        try:
            parsed_renames = json.loads(renames)
        except json.JSONDecodeError as exc:
            raise HTTPException(status_code=400, detail=f"Invalid renames JSON: {exc}") from exc
        if not isinstance(parsed_renames, dict):
            raise HTTPException(
                status_code=400, detail="renames must be a JSON object keyed by filename."
            )

    payload: list[tuple[str, bytes]] = []
    for upload in files:
        raw = await upload.read()
        payload.append((upload.filename or "", raw))

    try:
        return station_extractor.extract(payload, parsed_mapping, parsed_renames)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
