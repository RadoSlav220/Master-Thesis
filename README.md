# Local Digital Twin — Component Orchestration Platform

A prototype **Local Digital Twin** orchestration platform. It manages geospatial
datasets, registers external analytical **components** (ML / analytical services
exposed over REST), executes them against datasets, and visualizes the returned
geospatial results on an interactive map.

## Project overview

A **Digital Twin** is a live digital representation of a physical system — here, a
city. This platform is the **orchestration layer** of such a twin: it does *not*
perform analysis itself. Instead it coordinates heterogeneous external analytical
components through a common execution mechanism and persists their results for
visualization.

- **Component orchestration** — analytical models live behind REST endpoints. The
  platform registers them, sends a dataset (and its GeoJSON geometry) to the
  component, stores the result, and surfaces it. Swapping or adding a model needs no
  platform change — only a new registered endpoint.
- **Geospatial focus** — datasets and results are GeoJSON, rendered on MapLibre, so
  city-scale analytical output (air quality, traffic, …) is immediately visual.

### Thesis context

This repository is the practical artifact of a master's thesis. It demonstrates that
a platform can integrate geospatial datasets, invoke external analytical services
through a uniform interface, and visualize city-related results — the core capability
of a Local Digital Twin. This step focuses on **reproducibility and demonstration
readiness**: the whole system runs with a single command.

## Architecture

```
        React Frontend (MapLibre + MUI)
                  |  REST (/api → proxied)
                  v
         Spring Boot Backend (orchestration)  ───────►  Python Dataset
                  |                                      Analysis Service
                  |  1. fetch snapshot from a Data       (FastAPI + Pandas)
                  |     Source (mock external API)        - /analyze (structure)
                  |  2. auto-analyze + persist            - /extract-stations
                  v
       External Analytical Components (mock)
                  |  GeoJSON FeatureCollection
                  v
          Execution Results (PostgreSQL)
                  |
                  v
          MapLibre Visualization
```

The pipeline: register a **Data Source** → **fetch** a dataset snapshot for a time
period → the backend **auto-analyzes** it (via the Python service) and **persists** the
result → **map** the dataset's measurements to an analytical **component**'s expected
measurements → run the component → visualize the GeoJSON result on the map.

The frontend talks only to the backend REST API. The backend orchestrates external
components, delegates data-processing (structure analysis) to the Python
service, and persists data sources / datasets / executions in PostgreSQL. Analytical
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
├── sample-data/      Example GeoJSON datasets for the demo
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
http://localhost:3000 and either **fetch** a dataset from a data source or **upload**
one:

1. **Register a Data Source** — Data Sources → *Register Data Source* → type `API`,
   output format `GEOJSON` (or `CSV`).
2. **Fetch a dataset** — on that source, click *Fetch*, give it a name and a date range.
   The backend generates a snapshot, **auto-analyzes** it, and stores the analysis. The
   new dataset appears under Datasets. *(Alternatively: Datasets → Create Dataset →
   `GEOJSON` → upload a file from [`sample-data/`](sample-data/).)*
3. **Inspect the dataset** — open its details to see metadata, the persisted analysis
   (columns/properties), geometry types, and a map preview.
4. **Execute a component** — Executions → pick the dataset + a component (Air Quality /
   Traffic) → map the dataset's measurements to the component's expected measurements →
   *Execute*.
5. **Visualize results** — Map → *Result* mode → select the execution to see the
   returned GeoJSON features colored by value, plus a chart.

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
| `POST /data-sources/{id}/fetch`  | Fetch a dataset snapshot for a period; auto-analyze + store |
| `POST /datasets/upload`          | Multipart upload of a GeoJSON file                          |
| `GET /datasets/{id}/geojson`     | The stored GeoJSON FeatureCollection                        |
| `POST /datasets/{id}/analyze`    | (Re-)run structure analysis via the Python service          |

### Example

```bash
# Register a data source
curl -X POST http://localhost:8080/data-sources \
  -H "Content-Type: application/json" \
  -d '{"name":"Sofia AQ API","type":"API","outputFormat":"GEOJSON"}'

# Fetch a dataset snapshot from it (auto-analyzed + stored)
curl -X POST http://localhost:8080/data-sources/<source-id>/fetch \
  -H "Content-Type: application/json" \
  -d '{"name":"AQ Snapshot","startDate":"2026-07-01","endDate":"2026-07-05"}'

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

## Geospatial datasets (GeoJSON)

Datasets can carry a GeoJSON `FeatureCollection`. The backend stores the raw
content (as text — no PostGIS) and passes it to components at execution time, so
analytical results come back as GeoJSON ready to render on a map.

### Endpoints

| Method / path                 | Purpose                                             |
|-------------------------------|-----------------------------------------------------|
| `POST /datasets/upload`       | Multipart upload of a `.geojson` file               |
| `GET /datasets/{id}/geojson`  | Returns the stored `FeatureCollection`              |

`POST /datasets/upload` is `multipart/form-data` with parts: `file` (the GeoJSON),
`name`, and optional `description`. The backend validates that the file is a
`FeatureCollection` with a `features` array (basic structural check) and returns
`400` otherwise. Uploaded datasets get `type = GEOJSON` and `hasGeoJson = true`.

At execution time the component receives:

```json
{ "datasetId": "...", "geoJson": { "type": "FeatureCollection", "features": [ ... ] } }
```

### Sample data

Small example FeatureCollections live in [`sample-data/`](sample-data/):

- `sofia-air-quality.geojson` — 5 sensor Points
- `sofia-traffic.geojson` — LineStrings + a Polygon

### Example

```bash
# Upload a GeoJSON dataset
curl -X POST http://localhost:8080/datasets/upload \
  -F "file=@sample-data/sofia-air-quality.geojson" \
  -F "name=Sofia Air Quality"

# Fetch its geometry back
curl http://localhost:8080/datasets/<dataset-id>/geojson

# Execute the air-quality component -> result is a FeatureCollection with pm25 per feature
curl -X POST http://localhost:8080/executions \
  -H "Content-Type: application/json" \
  -d '{"datasetId":"<dataset-id>","componentId":"<component-id>"}'
```

## Data sources & fetching

Rather than only uploading files, datasets can be **fetched** from a registered
**Data Source**. A data source records an external feed (`type` = `API`; a `DATABASE`
type is reserved for later) and its `outputFormat` (`CSV` or `GEOJSON`).

Fetching a snapshot for a date range:

1. `POST /data-sources/{id}/fetch` with `{ name, startDate, endDate }`.
2. The backend generates a mock snapshot in the source's format (external fetch is
   mocked for now — no real network call), stores it as a new dataset, then **runs
   structure analysis once and persists the result** on the dataset.

Because a fetched snapshot is immutable, caching its analysis is safe — the dataset
records its `datasetType`, its `analysisResult`, and the `sourceId` it came from
(provenance). The Dataset Details page shows the persisted analysis without re-calling
the Python service.

## Dataset analysis service

A standalone **Python (FastAPI)** microservice handles data-processing so the backend
stays a pure orchestrator. It inspects dataset structure and extracts stations from CSV
uploads. See [`data-analysis-service/`](data-analysis-service/).

- **`POST /analyze`** (multipart `file`) → `{ "datasetType": "CSV", "columns": [...] }`
  or `{ "datasetType": "GEOJSON", "properties": [...] }`.
- **`POST /extract-stations`** (multipart `files` + `mapping`) → parses station CSV files
  into relational stations + long-format measurements.

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
| Data Sources    | Register external sources; **Fetch** a dataset for a date range     |
| Datasets        | List datasets; upload a GeoJSON file or create a plain dataset      |
| Dataset Details | Metadata, persisted analysis, feature count, geometry types, map preview |
| Components      | List components; register one (name, endpoint URL, description)     |
| Executions      | Select a dataset + component, map the dataset's measurements to the component's expected ones, execute, view result |
| Map             | Two modes — **Dataset** (preview geometry) and **Result** (execution output) — rendered as GeoJSON layers with a values chart |

### GeoJSON rendering

The Map and Dataset Details pages render a `FeatureCollection` using MapLibre
GeoJSON source + layers: `circle` for points, `line` for LineStrings, and `fill`
for Polygons/MultiPolygons. Features are colored on a green→red scale by a detected
numeric property (`value`, else `pm25`/`congestion`, else the first numeric field),
and point values are shown as labels.

### User flow

Register a **Data Source** → **Fetch** a dataset for a date range (auto-analyzed) →
open **Dataset Details** to see the persisted analysis, geometry, and map preview →
**Executions** page: select the dataset + a component, optionally tick which
columns/properties to keep and a row limit, then **Execute** → the result is a GeoJSON
`FeatureCollection` → open **Map**, switch to **Result** mode, and select the execution
to see the colored features plus a values chart. *(Uploading a GeoJSON file is an
alternative to fetching.)*
