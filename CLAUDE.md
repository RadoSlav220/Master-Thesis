# Project context for Claude

Guidance for AI assistants (and a quick orientation for humans) working in this
repository. This file is auto-loaded by Claude Code when present at the repo root.

## What this is

**Local Digital Twin — Component Orchestration Platform.** The practical artifact of a
master's thesis. It integrates geospatial datasets, invokes external analytical services
through a uniform interface, and visualizes city-related results — the core capability of
a Local Digital Twin. The platform is an **orchestration layer**: it does not perform
analysis itself.

See `README.md` for run instructions and API details; this file focuses on architecture
and the reasoning behind key decisions.

## Monorepo layout

- `backend/` — Spring Boot orchestration API (Java 25, Maven) + Dockerfile.
- `frontend/` — React + TypeScript (Vite; nginx in Docker). MUI, Axios, TanStack React
  Query, Zustand, React Router, MapLibre GL JS, Recharts.
- `data-analysis-service/` — Python 3.12 FastAPI + Pandas microservice (structure
  analysis + filtering). Uses ruff + pytest; CI runs both.
- `docker-compose.yml` (postgres + backend + frontend + data-analysis-service),
  `.env.example`, `sample-data/` (Sofia GeoJSON samples).

## The pipeline (the thesis story)

Data Source → **fetch** a snapshot for a period → **auto-analyze + persist** → optional
**filter** (column/property selection + row limit) → run an analytical **component** →
**visualize** the GeoJSON result on MapLibre. External source fetches and analytical
models are **mocked** for now.

## Key decisions and the "why" (read before changing related code)

- **Orchestration boundary** — data processing (analysis, filtering) is delegated to the
  Python service; the backend never does it itself. Preserve this separation.
- **Backend → Python HTTP client** — Spring `RestClient` **pinned to HTTP/1.1**
  (`JdkClientHttpRequestFactory` with `HttpClient.Version.HTTP_1_1`). The JDK client's
  default HTTP/2 upgrade corrupts multipart requests to uvicorn (422 / "invalid HTTP
  request"). Do **not** switch to WebClient — the project deliberately has no webflux.
  Multipart parts are built with `LinkedMultiValueMap` + `HttpEntity`/`ContentDisposition`,
  **not** `MultipartBodyBuilder` (which pulls in reactive-streams that aren't on the classpath).
- **Dataset storage** — one generic `content` column (formerly `geoJsonContent`). A
  fetched dataset also stores `datasetType`, a persisted `analysisResult`, and `sourceId`
  (provenance). Fetched snapshots are immutable, so caching their analysis is safe.
- **Persistence** — Hibernate `ddl-auto: update`. Known limitations already encountered:
  it cannot add NOT NULL columns to populated tables, and column renames leave orphaned
  columns behind. Add new columns **nullable**. Migrating to Flyway is a planned backlog item.
- **DataSource model** — `type` = `API` (with `DATABASE` reserved for later) + an
  `outputFormat` of `CSV` or `GEOJSON`.
- **Filtering (v1)** — column/property selection + row limit only (no value predicates).
  The applied filter is persisted on the execution as `filterSpec`. Filtered data feeds
  the model end-to-end for **GeoJSON**; CSV → model is deferred (mock components consume GeoJSON).
- **Mock components** — `/mock-components/air-quality` and `/traffic` return GeoJSON
  FeatureCollections. A `demo` Spring profile auto-registers them on startup.
- **Ops endpoints** — custom `/health` and `/info` (no Spring Actuator).
- **Docker** — Java 25 base images; the frontend nginx proxies `/api/` → backend
  (same-origin, no CORS), mirroring the Vite dev proxy.

## Constraints deliberately deferred (MVP scope)

No authentication, PostGIS, workflow chaining, or message queues. (Auth is now a planned
backlog epic; PostGIS/predicate-filtering/real-fetch are backlog too.)

## Conventions

- Backend: mirror existing entity/repository/DTO/service/controller patterns; reuse
  `NotFoundException` and the `GlobalExceptionHandler`.
- Frontend: MUI `Stack`/`Typography` need an `sx` prop present (an overload-resolution
  quirk in the installed MUI version) — route layout props through `sx`.
- Each part is checked in CI on push: backend `mvn package`, frontend lint + build,
  analysis-service ruff + pytest.
