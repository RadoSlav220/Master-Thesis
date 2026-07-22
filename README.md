# Master-Thesis — Digital Twin Orchestration Platform

A Spring Boot service that orchestrates external Digital Twin / ML components. It
exposes REST endpoints to register **components** and **datasets**, and to trigger
**executions** that bind a dataset to a component. A React frontend provides the
user interface and geospatial visualization.

## Project structure

```
.
├── backend/     Spring Boot orchestration API (Java 25, Maven)
│   ├── pom.xml
│   └── src/
├── frontend/    React + TypeScript UI (Vite)
│   └── src/
└── README.md
```

All backend Maven commands below are run from the `backend/` directory.

## Prerequisites

- **Java 25** (JDK)
- **Maven 3.9+**
- **PostgreSQL 16** running on `localhost:5432`
- **Node.js 20+** and **npm** (for the frontend)

## Database setup

The app expects a database named `orchestrator`. Create it once:

```sql
CREATE DATABASE orchestrator;
```

Tables are created automatically on first boot (Hibernate `ddl-auto: update`).

## Configuration

Datasource settings resolve from environment variables, with local-dev defaults
in `application.yml`:

| Variable      | Default                                              |
|---------------|------------------------------------------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:5432/orchestrator`      |
| `DB_USERNAME` | `postgres`                                           |
| `DB_PASSWORD` | *(empty)*                                            |

Credentials are **not** committed. Provide the password in one of two ways:

**Option A — local profile file** (`backend/src/main/resources/application-local.yml`, git-ignored):

```yaml
spring:
  datasource:
    username: postgres
    password: your-password
```

**Option B — environment variables:**

```bash
export DB_PASSWORD=your-password
```

## Running the backend

From the `backend/` directory, with the local profile:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Or with environment variables set:

```bash
cd backend
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. Startup is confirmed by the
`Started OrchestratorApplication` log line.

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
