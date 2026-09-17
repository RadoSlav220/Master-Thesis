# Local Digital Twin — Component Orchestration Platform

A prototype **Local Digital Twin** orchestration platform. It manages geospatial
datasets, registers external analytical **components** (ML / analytical services
exposed over REST), executes them against datasets, and surfaces the returned
results.

## Project overview

A **Digital Twin** is a live digital representation of a physical system — here, a
city. This platform is the **orchestration layer** of such a twin: it does *not*
perform analysis itself. Instead it coordinates heterogeneous external analytical
components through a common execution mechanism and persists their results.

- **Component orchestration** — analytical models live behind REST endpoints. The
  platform registers them, sends a dataset to the component, stores the result, and
  surfaces it. Swapping or adding a model needs no platform change — only a new
  registered endpoint.
- **Geospatial data** — datasets are station + measurement data ingested from CSV;
  the internal per-station representation and component I/O use GeoJSON, so city-scale
  analytical output (air quality, traffic, …) is geometry-aware. *(A dedicated result
  map view is deferred — see below.)*

### Thesis context

This repository is the practical artifact of a master's thesis. It demonstrates that
a platform can integrate geospatial datasets, invoke external analytical services
through a uniform interface, and surface city-related results — the core capability
of a Local Digital Twin. This step focuses on **reproducibility and demonstration
readiness**: the whole system runs with a single command.

## Architecture

```
              React Frontend (MUI)
                  |  REST (/api → proxied)
                  v
         Spring Boot Backend (orchestration)  ───────►  Python Dataset
                  |                                      Analysis Service
                  |  1. upload station CSV(s)            (FastAPI + Pandas)
                  |  2. auto-analyze + persist            - /analyze (structure)
                  v                                       - /extract-stations
       External Analytical Components (mock)
                  |  GeoJSON FeatureCollection
                  v
          Execution Results (PostgreSQL)
```

The pipeline: **upload** station + measurement CSV files (assigning each column a role)
→ the backend **extracts + persists** stations/measurements (via the Python service) →
**map** the dataset's measurements to an analytical **component**'s expected
measurements → run the component → inspect the stored result.

Result **visualization** is deferred: an interactive result map was removed (the model
output shape — forecasts in time and/or space — is still being decided) and results are
shown as raw JSON for now.

The frontend talks only to the backend REST API. The backend orchestrates external
components, delegates data-processing (structure analysis, station extraction) to the
Python service, and persists datasets / executions in PostgreSQL. Analytical
logic stays in the (currently mock) external components and the specialized Python
service.

## Project structure

```
.
├── backend/          Spring Boot orchestration API (Java 25, Maven)
│   ├── Dockerfile
│   └── src/
├── frontend/         React + TypeScript UI (Vite, nginx in Docker)
│   ├── Dockerfile
│   └── src/
├── data-analysis-service/   Python FastAPI dataset-analysis microservice
│   ├── Dockerfile
│   └── app/
├── sample-data/      Example Sofia station CSVs for the demo
├── docker-compose.yml
├── .env.example
└── README.md
```

## Quick start (Docker)

The entire stack — PostgreSQL, backend, frontend — runs with one command:

```bash
cp .env.example .env          # optional; sensible defaults are built in
docker compose up --build
```

Then open **http://localhost:3000**.

On startup the backend runs with the `demo` profile, which **auto-registers** the two
mock analytical components (Air Quality, Traffic), so no manual setup is needed for a
demo. Services:

| Service   | URL / port                    | Notes                                  |
|-----------|-------------------------------|----------------------------------------|
| Frontend  | http://localhost:3000         | nginx serving the React build          |
| Backend   | http://localhost:8080         | Spring Boot REST API                   |
| Postgres  | internal (`postgres:5432`)    | data persisted in the `pgdata` volume  |

Configuration is via environment variables (see `.env.example`):

- Backend: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- Frontend: `VITE_API_URL` (default `/api`, proxied by nginx to the backend)

> **Note on Java:** the project targets **Java 25** (see `backend/pom.xml`), so the
> backend image uses a Java 25 base image rather than Java 21.

## Demo scenario

With the stack running (the two mock analytical components are auto-registered), open
http://localhost:3000 and **upload** a station dataset:

1. **Create a dataset** — Datasets → *Create Dataset* → upload one or more station CSV
   files from [`sample-data/`](sample-data/) (e.g. `sofia-stations-air-quality.csv`).
   Assign each column a **role** (`STATION_ID` / `LATITUDE` / `LONGITUDE` / `TIMESTAMP` /
   `STATION_ATTRIBUTE` / `MEASUREMENT` / `IGNORE`); optionally give MEASUREMENT columns a
   canonical name. The backend extracts stations + measurements and persists them.
2. **Inspect the dataset** — open its details to see metadata, the station-location map,
   and the stations / measurements tables.
3. **Execute a component** — Executions → pick the dataset + a component (Air Quality /
   Traffic) → map the dataset's measurements to the component's expected measurements →
   *Execute*.
4. **Inspect the result** — open the execution to see the returned GeoJSON
   `FeatureCollection` (shown as JSON; a dedicated result view is deferred).

## Health & info endpoints

| Endpoint  | Response                                                      |
|-----------|--------------------------------------------------------------|
| `GET /health` | `{"status":"UP"}`                                        |
| `GET /info`   | `{"name":"Local Digital Twin Platform","version":"0.1.0"}` |

## Local development (without Docker)

### Prerequisites

- **Java 25** (JDK), **Maven 3.9+**
- **PostgreSQL 16** running on `localhost:5432` with a database named `orchestrator`
- **Node.js 20+** and **npm**
- **Python 3.12** (for the dataset analysis service)

### Backend

```bash
cd backend
mvn spring-boot:run
```

Datasource settings resolve from environment variables (`DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`) with local defaults in `application.yml`. Credentials are not
committed — provide the password via a git-ignored
`backend/src/main/resources/application-local.yml` (run with
`-Dspring-boot.run.profiles=local`) or `export DB_PASSWORD=...`.

The API starts on `http://localhost:8080` (`Started OrchestratorApplication` in logs).
Analysis delegates to the Python service (default `http://localhost:8000`),
so start that too if you exercise those features.

### Dataset analysis service

```bash
cd data-analysis-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

### Frontend

```bash
cd frontend
npm install
npm run dev          # dev server on http://localhost:5173
```

In dev, `/api/*` calls are proxied to `http://localhost:8080` (see
`frontend/vite.config.ts`). Start the backend first.

## API

Each resource supports create (`POST`), list (`GET`), and fetch-by-id (`GET /{id}`).

| Resource      | Base path        |
|---------------|------------------|
| Data Sources  | `/data-sources`  |
| Components     | `/components`     |
| Datasets       | `/datasets`       |
| Executions     | `/executions`     |

Additional endpoints:

| Method / path                    | Purpose                                                    |
|----------------------------------|------------------------------------------------------------|
| `POST /datasets/upload-stations` | Multipart upload of one or more station CSVs + a column-role mapping; extracts stations/measurements |
| `POST /datasets/upload`          | Multipart upload of a single CSV file                      |
| `GET /datasets/{id}/stations`    | Stations extracted for a station-based dataset             |
| `GET /datasets/{id}/measurements`| A dataset's measurements (long format, capped by `limit`)  |
| `GET /datasets/{id}/download`    | Download the dataset as a file: content-based datasets as a single CSV; station-based datasets as a `.zip` of `stations.csv` + `measurements.csv` (round-trippable via Create Dataset) |
| `POST /datasets/{id}/analyze`    | (Re-)run structure analysis via the Python service          |
| `POST /data-sources/{id}/fetch`  | Fetch a dataset snapshot (parked API-fetch path; mocked)   |

### Example

```bash
# Upload a station dataset (CSV + a per-file column-role mapping)
curl -X POST http://localhost:8080/datasets/upload-stations \
  -F "files=@sample-data/sofia-stations-air-quality.csv" \
  -F "name=Sofia Air Quality" \
  -F 'mapping={"sofia-stations-air-quality.csv":{"station":"STATION_ID","lat":"LATITUDE","lon":"LONGITUDE","ts":"TIMESTAMP","pm25":"MEASUREMENT"}}'

# Trigger an execution, mapping the dataset's measurements to the component's expected ones
curl -X POST http://localhost:8080/executions \
  -H "Content-Type: application/json" \
  -d '{"datasetId":"<dataset-id>","componentId":"<component-id>",
       "measurementMapping":{"pm25":"pm25"}}'

# List executions
curl http://localhost:8080/executions
```

## Executing components

The platform is an **orchestration layer**: it does not run analytical models
itself. A registered component points (via `endpointUrl`) at an external REST
service. Triggering an execution invokes that service **synchronously**, stores
the returned JSON in the execution's `result`, and sets the status.

Execution lifecycle: `RUNNING` → `COMPLETED` (result stored) or `FAILED`
(`errorMessage` stored). A failed invocation is still persisted and returned with
HTTP `201` — the failure is a recorded outcome, not a request error.

### Mock components

Two in-app endpoints simulate future external services, so the flow is demoable
without deploying a real model. Each accepts
`POST { "datasetId": "<uuid>", "geoJson": <FeatureCollection|null> }` and returns a
**GeoJSON FeatureCollection**: if the request carries dataset geometry, each input
feature is echoed with a computed metric attached; otherwise a small static set of
Sofia points is returned.

| Component    | Endpoint                                       | Metric       |
|--------------|------------------------------------------------|--------------|
| Air Quality  | `POST /mock-components/air-quality`            | `pm25`       |
| Traffic      | `POST /mock-components/traffic`                | `congestion` |

### End-to-end example

```bash
# 1. Create a dataset -> note its id
curl -X POST http://localhost:8080/datasets \
  -H "Content-Type: application/json" \
  -d '{"name":"sofia-sensors","type":"timeseries","description":"demo"}'

# 2. Register a component pointing at a mock endpoint -> note its id
curl -X POST http://localhost:8080/components \
  -H "Content-Type: application/json" \
  -d '{"name":"Air Quality","endpointUrl":"http://localhost:8080/mock-components/air-quality","description":"mock"}'

# 3. Execute -> returns a COMPLETED execution with the component result
curl -X POST http://localhost:8080/executions \
  -H "Content-Type: application/json" \
  -d '{"datasetId":"<dataset-id>","componentId":"<component-id>"}'

# 4. Retrieve the stored result
curl http://localhost:8080/executions/<execution-id>
```

Swap the `endpointUrl` for `.../mock-components/traffic` to run the Traffic component.

## Data sources & fetching (parked)

An earlier ingestion path let datasets be **fetched** from a registered **Data
Source** (an external `API` feed) instead of uploaded. This path is **parked** in
favor of manual CSV upload (it is kept in the codebase but not the focus, and may be
removed): `POST /data-sources/{id}/fetch` generates a **mock CSV** snapshot (no real
network call), stores it as a dataset, and runs structure analysis once.

Because a fetched snapshot is immutable, caching its analysis is safe — the dataset
records its `datasetType`, its `analysisResult`, and the `sourceId` it came from
(provenance).

## Dataset analysis service

A standalone **Python (FastAPI)** microservice handles data-processing so the backend
stays a pure orchestrator. It inspects dataset structure and extracts stations from CSV
uploads. See [`data-analysis-service/`](data-analysis-service/).

- **`POST /analyze`** (multipart `file`, CSV) → `{ "datasetType": "CSV", "columns": [...] }`.
- **`POST /extract-stations`** (multipart `files` + `mapping`, optional `renames`) → parses
  station CSV files into relational stations + long-format measurements. `renames` optionally
  gives MEASUREMENT columns a canonical name (`{filename: {column: name}}`) so differently-named
  columns unify into one measurement type; readings are deduplicated by
  `(stationExternalId, timestamp, measurementType)` with last-value-wins.

How the backend uses it:

- **Analysis** — persisted automatically on fetch; also re-runnable via
  `POST /datasets/{id}/analyze` (the Dataset Details **Analyze** button).
- **Station extraction** — station-based CSV uploads are transformed into the dataset's
  stored representation via `/extract-stations`.

Configured via `dataset.analysis.service.url` (env `DATASET_ANALYSIS_SERVICE_URL`,
default `http://localhost:8000`). The service is included in `docker-compose.yml`, so
`docker compose up --build` runs it alongside the rest of the stack.

## Frontend

A React + TypeScript single-page app (Vite) that provides the UI and visualization
layer. It talks only to the backend REST API — no analytical logic lives here.

**Stack:** React, TypeScript, Vite, Material UI, Axios, TanStack React Query
(server state), Zustand (selection state), React Router, MapLibre GL JS (map),
Recharts (charts).

### Setup & running

```bash
cd frontend
npm install          # first time only
npm run dev          # dev server on http://localhost:5173
```

In development, API calls to `/api/*` are proxied to the backend at
`http://localhost:8080` (see `frontend/vite.config.ts`), which avoids browser CORS
without any backend change. To point at a different backend, set `VITE_API_URL`
(see `frontend/.env.example`).

Start the backend first, then the frontend.

```bash
npm run build        # type-check + production build into frontend/dist
```

### Pages

| Page            | Purpose                                                              |
|-----------------|---------------------------------------------------------------------|
| Dashboard       | Counts of datasets, components, executions                          |
| Data Sources    | Register external sources; **Fetch** a dataset (parked path)        |
| Datasets        | List datasets; upload station CSVs (Create Dataset) or a single CSV |
| Dataset Details | Metadata, persisted analysis, station-location map, stations/measurements tables |
| Components      | List components; register one (name, endpoint URL, description)     |
| Executions      | Select a dataset + component, map the dataset's measurements to the component's expected ones, execute, view result |

### Station map rendering

The Dataset Details page renders a station-location `FeatureCollection` (built from a
dataset's extracted stations) using a MapLibre GeoJSON source + a `circle` layer, with a
click-to-show-metadata popup. Stations without coordinates are omitted.

### User flow

Create a **dataset** via **Datasets → Create Dataset** (upload station CSV files and
assign each column a role) → open **Dataset Details** to see the persisted stations,
the station map, and the measurements → **Executions** page: select the dataset + a
component, map the dataset's measurements to the component's expected ones, then
**Execute** → the result is a GeoJSON `FeatureCollection`, stored on the execution and
shown as JSON (a dedicated result view is deferred).
