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
         Spring Boot Backend (orchestration)
                  |  REST (synchronous invoke)
                  v
       External Analytical Components (mock)
                  |  GeoJSON FeatureCollection
                  v
          Execution Results (PostgreSQL)
                  |
                  v
          MapLibre Visualization
```

The frontend talks only to the backend REST API. The backend orchestrates external
components and persists datasets/executions in PostgreSQL. Analytical logic stays in
the (currently mock) external components.

## Project structure

```
.
├── backend/          Spring Boot orchestration API (Java 25, Maven)
│   ├── Dockerfile
│   └── src/
├── frontend/         React + TypeScript UI (Vite, nginx in Docker)
│   ├── Dockerfile
│   └── src/
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

With the stack running (components already auto-registered), open
http://localhost:3000 and:

1. **Upload a GeoJSON dataset** — Datasets → *Create Dataset* → type `GEOJSON` →
   choose a file from [`sample-data/`](sample-data/).
2. **Select an analytical component** — the Air Quality / Traffic models are already
   listed under Components.
3. **Execute the component** — Executions → pick the dataset + component → *Execute*.
4. **Visualize results** — Map → *Result* mode → select the execution to see the
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

| Resource     | Base path      |
|--------------|----------------|
| Components   | `/components`  |
| Datasets     | `/datasets`    |
| Executions   | `/executions`  |

### Example

```bash
# Create a component
curl -X POST http://localhost:8080/components \
  -H "Content-Type: application/json" \
  -d '{"name":"anomaly-detector","endpointUrl":"http://localhost:9000/run","description":"demo"}'

# Create a dataset
curl -X POST http://localhost:8080/datasets \
  -H "Content-Type: application/json" \
  -d '{"name":"sensor-readings","type":"timeseries","description":"demo"}'

# Trigger an execution (use the ids returned above)
curl -X POST http://localhost:8080/executions \
  -H "Content-Type: application/json" \
  -d '{"datasetId":"<dataset-id>","componentId":"<component-id>"}'

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
| Datasets        | List datasets; upload a GeoJSON file or create a plain dataset      |
| Dataset Details | Metadata, feature count, geometry types, and a map preview          |
| Components      | List components; register one (name, endpoint URL, description)     |
| Executions      | Select a dataset + component, execute, view result JSON             |
| Map             | Two modes — **Dataset** (preview geometry) and **Result** (execution output) — rendered as GeoJSON layers with a values chart |

### GeoJSON rendering

The Map and Dataset Details pages render a `FeatureCollection` using MapLibre
GeoJSON source + layers: `circle` for points, `line` for LineStrings, and `fill`
for Polygons/MultiPolygons. Features are colored on a green→red scale by a detected
numeric property (`value`, else `pm25`/`congestion`, else the first numeric field),
and point values are shown as labels.

### User flow

Upload a GeoJSON dataset → open **Dataset Details** to see its geometry, feature
count, and types → register a component (e.g. the Air Quality mock) → **Executions**
page: select the dataset + component and click **Execute** → the result is a
GeoJSON `FeatureCollection` → open **Map**, switch to **Result** mode, and select
the execution to see the colored features plus a values chart.
