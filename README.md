# Local Digital Twin — Component Orchestration Platform

A prototype **Local Digital Twin** orchestration platform. It manages geospatial
datasets, registers external analytical **components** (ML / analytical services
exposed over REST), executes them against datasets, and surfaces the returned
results.

## Project overview

A **Digital Twin** is a live digital representation of a physical system — here, a
city. This platform is the **orchestration layer** of such a twin: it does *not*
perform data processing or analysis itself. Instead it coordinates two kinds of
external system through a common mechanism and persists what they return:

- **GraFlex** — a standalone data platform that stores and processes dataset rows
  (reshaping an upload, importing it, cleaning it). The backend drives it over HTTP.
- **Analytical components** — ML / analytical models behind REST endpoints. The
  platform sends a dataset to a component, stores the result, and surfaces it.

Key ideas:

- **Component orchestration** — analytical models live behind REST endpoints. Swapping
  or adding a model needs no platform change — only a new registered endpoint.
- **Data platform boundary** — a dataset's rows live in **GraFlex**, not the platform
  DB. The platform keeps only *metadata* (name, origin, the column-role mapping the user
  authored, and a pointer to the GraFlex entity) and reaches the data **over HTTP,
  never GraFlex's database directly**.
- **Geospatial data** — datasets are station + measurement data ingested from CSV;
  the per-station representation and component I/O use GeoJSON, so city-scale analytical
  output (air quality, traffic, …) is geometry-aware. *(A dedicated result map view is
  deferred — see below.)*

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
         Spring Boot Backend (orchestration)  ───────►  GraFlex (data platform)
                  |                                      (FastAPI + Pandas + its own
                  |  1. upload raw station CSV(s)         Postgres)
                  |     + a column-role mapping            - reshape (dedup, pivot)
                  |  2. GraFlex reshapes + imports;        - author schemas + import
                  |     platform stores a pointer          - read stations/measurements
                  v                                        - (cleaning / EDA: planned)
       External Analytical Components
                  |  GeoJSON FeatureCollection
                  v
          Platform DB (PostgreSQL): dataset metadata,
          components, executions, results
```

The pipeline: **upload** raw station + measurement CSV files (assigning each column a
role) → the backend forwards them to **GraFlex**, which reshapes them (deduplicate
stations, pivot measurements wide), authors its own entity schemas, and imports the rows
into **its own** Postgres; the platform records the column-role mapping + a GraFlex
entity pointer → **map** the dataset's measurements to an analytical **component**'s
expected measurements → run the component → inspect the stored result.

Result **visualization** is deferred: an interactive result map was removed (the model
output shape — forecasts in time and/or space — is still being decided) and results are
shown as raw JSON for now.

The frontend talks only to the backend REST API. The backend orchestrates external
components and drives GraFlex over HTTP; it performs no data processing itself. There
are **two databases**: the platform's own (metadata) and GraFlex's (dataset rows) — the
platform never opens a SQL connection to GraFlex's.

## Project structure

This repository (`Project/`) is the **orchestration platform**. GraFlex lives in a
**sibling repository** (`../graflex`), not in this tree.

```
.
├── backend/          Spring Boot orchestration API (Java 25, Maven)
│   ├── Dockerfile
│   └── src/
├── frontend/         React + TypeScript UI (Vite, nginx in Docker)
│   ├── Dockerfile
│   └── src/
├── sample-data/      Example Sofia station CSVs for the demo
├── docs/             Architecture + integration docs
├── docker-compose.yml   Platform-only stack (needs an external GraFlex)
└── README.md
```

A **unified** compose that runs the platform *and* GraFlex together lives one level up,
at `../docker-compose.yml` (parent of both repos).

## Quick start (Docker)

The entire stack — both Postgres databases, GraFlex, backend, frontend — runs from the
**parent-level** compose (one directory above this repo, alongside `graflex/`):

```bash
cd ..                         # the directory containing Project/ and graflex/
docker compose up --build
```

Then open **http://localhost:3000**.

The backend starts with **no components registered** (the former `demo`-profile mock
components were removed). To execute a component, register one pointing at a real
analytical REST endpoint first (see *Executing components* below). Services:

| Service          | URL / port                 | Notes                                        |
|------------------|----------------------------|----------------------------------------------|
| Frontend         | http://localhost:3000      | nginx serving the React build                |
| Backend          | http://localhost:8080      | Spring Boot REST API                         |
| GraFlex API      | http://localhost:8001      | FastAPI data platform                        |
| Platform Postgres| internal                   | dataset metadata, components, executions     |
| GraFlex Postgres | internal                   | dataset rows (schemas, raw tables)           |

> **Platform-only:** this repo's own `docker-compose.yml` runs just the platform
> (postgres + backend + frontend). The backend still needs GraFlex, so start GraFlex
> separately and point the backend at it via `GRAFLEX_SERVICE_URL`. For a complete,
> self-contained run, prefer the parent-level compose above.

Configuration is via environment variables:

- Backend DB: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- Backend → GraFlex: `GRAFLEX_SERVICE_URL` (default `http://localhost:8001`)
- Frontend: `VITE_API_URL` (default `/api`, proxied by nginx to the backend)

> **Note on Java:** the project targets **Java 25** (see `backend/pom.xml`), so the
> backend image uses a Java 25 base image.

## Demo scenario

With the stack running, open http://localhost:3000 and **upload** a station dataset:

1. **Create a dataset** — Datasets → *Create Dataset* → upload one or more station CSV
   files from [`sample-data/`](sample-data/) (e.g. `sofia-stations-air-quality.csv`).
   Assign each column a **role** (`STATION_ID` / `LATITUDE` / `LONGITUDE` / `TIMESTAMP` /
   `STATION_ATTRIBUTE` / `MEASUREMENT` / `IGNORE`); optionally give MEASUREMENT columns a
   canonical name. The backend forwards the files + mapping to GraFlex, which reshapes and
   imports them; the platform stores the mapping + a GraFlex entity pointer.
2. **Inspect the dataset** — open its details to see metadata, the station-location map,
   and the stations / measurements tables (all read back from GraFlex over HTTP).
3. **Execute a component** — register a component pointing at an analytical REST endpoint,
   then Executions → pick the dataset + that component → map the dataset's measurements to
   the component's expected measurements → *Execute*.
4. **Inspect the result** — open the execution to see the returned GeoJSON
   `FeatureCollection` (shown as JSON; a dedicated result view is deferred).

## Health & info endpoints

| Endpoint      | Response                                                     |
|---------------|--------------------------------------------------------------|
| `GET /health` | `{"status":"UP"}`                                            |
| `GET /info`   | `{"name":"Local Digital Twin Platform","version":"0.1.0"}`   |

## Local development (without Docker)

### Prerequisites

- **Java 25** (JDK), **Maven 3.9+**
- **PostgreSQL 16** on `localhost:5432` with a database named `orchestrator`
- **Node.js 20+** and **npm**
- **GraFlex** running and reachable (its API + its own Postgres) — see the `graflex`
  repo; the backend talks to it over HTTP

### Backend

```bash
cd backend
GRAFLEX_SERVICE_URL=http://localhost:8001 mvn spring-boot:run
```

Datasource settings resolve from environment variables (`DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`) with local defaults in `application.yml`. Credentials are not
committed — provide the password via a git-ignored
`backend/src/main/resources/application-local.yml` (run with
`-Dspring-boot.run.profiles=local`) or `export DB_PASSWORD=...`.

The API starts on `http://localhost:8080` (`Started OrchestratorApplication` in logs).
Dataset upload + read-back require GraFlex, so start it first (default
`http://localhost:8001`).

### GraFlex

GraFlex is a separate project (`../graflex`). Run its HTTP API + Postgres per that
repo's instructions; by convention it serves on port **8001** locally.

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
| Components    | `/components`     |
| Datasets      | `/datasets`       |
| Executions    | `/executions`     |

Additional endpoints:

| Method / path                     | Purpose                                                                      |
|-----------------------------------|------------------------------------------------------------------------------|
| `POST /datasets/upload-stations`  | Multipart upload of one or more station CSVs + a column-role mapping; reshaped + imported into GraFlex (the only ingestion path) |
| `GET /datasets/{id}/stations`     | A dataset's stations (read from GraFlex) — drives the station map            |
| `GET /datasets/{id}/measurements` | A dataset's measurements (long format, capped by `limit`; read from GraFlex) |
| `GET /datasets/{id}/stats`        | Station + measurement counts (read from GraFlex)                             |
| `GET /datasets/{id}/download`     | Download the dataset as a `.zip` of `stations.csv` + `measurements.csv` (round-trippable via Create Dataset) |

### Example

```bash
# Upload a station dataset (CSV files + a per-file column-role mapping)
curl -X POST http://localhost:8080/datasets/upload-stations \
  -F "name=Sofia Air Quality" \
  -F "files=@sample-data/sofia-stations-air-quality.csv" \
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

## Data processing: GraFlex

Data-processing lives in **GraFlex**, a standalone data platform in a sibling repo,
reached over HTTP so the backend stays a pure orchestrator. GraFlex owns a dataset's
rows in its own Postgres. The platform uses these GraFlex endpoints:

- **`POST /datasets/station-upload`** (multipart `files` + `mapping`, optional `renames`,
  `dataset_name`, `external_dataset_id`) → reshapes the raw CSVs (deduplicate stations,
  pivot measurements wide), authors a static station schema + a wide timeseries
  measurement schema, and imports both. `renames` optionally gives MEASUREMENT columns a
  canonical name so differently-named columns unify into one measurement type; readings
  deduplicate by `(stationExternalId, timestamp, measurementType)` with last-value-wins.
- **`GET /datasets/{entity}/stations`**, **`/measurements`**, **`/stats`** → read the
  imported rows back (the measurement table is unpivoted to long format) for the station
  map + detail tables.

Configured via `graflex.service.url` (env `GRAFLEX_SERVICE_URL`, default
`http://localhost:8001`). GraFlex runs as a service in the parent-level compose.

> **History:** an earlier Python `data-analysis-service` performed CSV structure analysis
> and station extraction. It has been **retired** — GraFlex now owns reshaping, import,
> and (planned) cleaning/EDA. The standalone `POST /datasets/upload` and
> `POST /datasets/{id}/analyze` endpoints were removed with it; station upload is the sole
> ingestion path.

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

Start the backend (and GraFlex) first, then the frontend.

```bash
npm run build        # type-check + production build into frontend/dist
```

### Pages

| Page            | Purpose                                                              |
|-----------------|---------------------------------------------------------------------|
| Dashboard       | Counts of datasets, components, executions                          |
| Datasets        | List datasets; upload station CSVs (Create Dataset)                 |
| Dataset Details | Metadata, station-location map, stations/measurements tables (read from GraFlex) |
| Components      | List components; register one (name, endpoint URL, description)     |
| Executions      | Select a dataset + component, map the dataset's measurements to the component's expected ones, execute, view result |

### Station map rendering

The Dataset Details page renders a station-location `FeatureCollection` (built from a
dataset's stations, read from GraFlex) using a MapLibre GeoJSON source + a `circle`
layer, with a click-to-show-metadata popup. Stations without coordinates are omitted.

### User flow

Create a **dataset** via **Datasets → Create Dataset** (upload station CSV files and
assign each column a role) → open **Dataset Details** to see the stations, the station
map, and the measurements (read back from GraFlex) → **Executions** page: select the
dataset + a component, map the dataset's measurements to the component's expected ones,
then **Execute** → the result is a GeoJSON `FeatureCollection`, stored on the execution
and shown as JSON (a dedicated result view is deferred).
