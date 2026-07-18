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
without deploying a real model. Each accepts `POST { "datasetId": "<uuid>" }`.

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

| Page        | Purpose                                                        |
|-------------|----------------------------------------------------------------|
| Dashboard   | Counts of datasets, components, executions                     |
| Datasets    | List datasets; create one (name, type CSV/GEOJSON, description) |
| Components  | List components; register one (name, endpoint URL, description) |
| Executions  | Select a dataset + component, execute, view result JSON        |
| Map         | Visualize a completed execution's geospatial results + chart   |

### User flow

Create dataset → register a component (e.g. the Air Quality mock endpoint) →
Executions page: select both and click **Execute** → view the result JSON →
open **Map** and select the execution to see markers and a values chart.

The map/chart normalize the result client-side: they read `latitude`/`longitude`
and pick a metric (`value`, else `pm25`/`congestion`, else the first numeric
field), so both mock components render without any backend change.
