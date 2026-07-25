# Dataset Analysis Service

A standalone Python (FastAPI) microservice — the first data-processing component of
the Local Digital Twin platform. It inspects an uploaded dataset file and returns its
**structure**: column names for CSV, or the union of feature property keys for
GeoJSON. It performs no filtering, transformation, or model execution — that is
foundation for future work.

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
    main.py                       # FastAPI app: POST /analyze, GET /health
    services/dataset_analyzer.py  # CSV (pandas) + GeoJSON analysis
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

`multipart/form-data` with a single `file` field.

**CSV** → 

```json
{ "datasetType": "CSV", "columns": ["timestamp", "temperature", "humidity"] }
```

**GeoJSON** (a `FeatureCollection`) → 

```json
{ "datasetType": "GEOJSON", "properties": ["pm25", "temperature"] }
```

Returns **400 Bad Request** for an unsupported file type, malformed CSV, or malformed
GeoJSON.

### `GET /health`

```json
{ "status": "UP" }
```

## Examples

```bash
# GeoJSON
curl -F "file=@../sample-data/sofia-air-quality.geojson" http://localhost:8000/analyze

# CSV
printf 'timestamp,temperature,humidity\n1,20,50\n' > /tmp/sample.csv
curl -F "file=@/tmp/sample.csv" http://localhost:8000/analyze
```
