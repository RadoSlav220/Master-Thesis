# Dataset Analysis Service

A standalone Python (FastAPI) microservice — the first data-processing component of
the Local Digital Twin platform. It inspects an uploaded **CSV** dataset file and
returns its **structure** (its column names), and extracts stations + measurements
from station-CSV uploads. It performs no filtering, transformation, or model
execution — that is foundation for future work.

Orchestration stays in the Spring Boot backend, which forwards dataset files here.

## Stack

- Python 3.12
- FastAPI
- Pandas
- Uvicorn

## Project structure

```
data-analysis-service/
  app/
    main.py                       # FastAPI app: POST /analyze, POST /extract-stations, GET /health
    services/dataset_analyzer.py  # CSV structure analysis (pandas)
    services/station_extractor.py # station-CSV -> relational stations + measurements
    models/analysis_response.py   # Pydantic response model
  requirements.txt
  Dockerfile
```

## Running locally

```bash
cd data-analysis-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

## API

### `POST /analyze`

`multipart/form-data` with a single `file` field (CSV only).

**CSV** → 

```json
{ "datasetType": "CSV", "columns": ["timestamp", "temperature", "humidity"] }
```

Returns **400 Bad Request** for an unsupported file type or malformed CSV.

### `POST /extract-stations`

`multipart/form-data` with `files` (one or more station CSVs) + a `mapping` JSON
(per-file column→role), and an optional `renames` JSON. Parses the CSVs into
relational stations + long-format measurements.

### `GET /health`

```json
{ "status": "UP" }
```

## Examples

```bash
# CSV structure analysis
printf 'timestamp,temperature,humidity\n1,20,50\n' > /tmp/sample.csv
curl -F "file=@/tmp/sample.csv" http://localhost:8000/analyze
```
