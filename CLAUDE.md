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
  analysis + filtering). Uses ruff + pytest.
- `docker-compose.yml` (postgres + backend + frontend + data-analysis-service),
  `.env.example`, `sample-data/` (Sofia GeoJSON samples).
- Each subproject has its **own `.gitignore`** (language/build rules); the root
  `.gitignore` holds only cross-cutting rules (OS/editor/env/secrets).
- **CI** (`.github/workflows/build.yml`) runs three jobs on push: `build-backend`
  (`mvn package`), `build-frontend` (lint + build), `build-analysis-service`
  (ruff + pytest).

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
- **Dataset origin & provenance** — a `datasetOrigin` enum (`UPLOAD`/`API`/`DATABASE`)
  records where a dataset came from, plus a nullable `provenance` JSON column holding the
  origin-specific detail (for `API`: the query-parameter values used at fetch time). The
  provenance shape is a **sealed `DatasetProvenance` hierarchy** (`ApiProvenance` /
  `DatabaseProvenance` / `UploadProvenance`) serialized with a Jackson `"type"`
  discriminator. Chosen over per-origin nullable columns or JPA entity inheritance: the
  differences are *data*, not *behavior*, so a discriminator enum + typed JSON blob keeps
  the schema flat while staying type-safe in code. `sourceId` stays top-level as the
  canonical source pointer.
- **Persistence** — Hibernate `ddl-auto: update`. Known limitations: it cannot add NOT
  NULL columns to populated tables, and column renames/removals leave orphaned columns
  behind. Add new columns **nullable**. `@Enumerated(STRING)` enums get a generated CHECK
  constraint on their column. In early dev, the local Postgres volume (`pgdata`) can just
  be pruned to shed accumulated schema drift. Flyway/migrations are the **lowest-priority**
  backlog item — do not design around them or treat them as a prerequisite.
- **DataSource model** — `type` is a `DataSourceType` **enum** = `API` (with `DATABASE`
  reserved for later) + an `outputFormat` of `CSV` or `GEOJSON`. At registration a source
  also declares the **query parameters** its API expects (name, `required` flag, optional
  `defaultValue`). These definitions are stored **relationally** as a
  `@ElementCollection<QueryParameterDefinition>` (table `data_source_query_parameters`,
  FK `data_source_id`), not a JSON blob — chosen so they're queryable and Hibernate-managed.
  The `QueryParameter` record stays in `dto/` as the wire contract. Fetching a snapshot is
  mocked by `DataSourceFetcher` (no real HTTP yet). Future `DATABASE` sources will need
  type-specific config (connection/query), likely a `DataSourceConfig` sealed hierarchy
  mirroring `DatasetProvenance` below — not yet built.
- **Fetch is parameterized by the source's registered query parameters** — the fetch
  request carries only a dataset `name` + a `Map<String,String> queryParameters` (values
  for the source's registered params). There are **no dedicated start/end date fields**:
  a time window, if a source needs one, is just registered as ordinary query parameters.
  The frontend Fetch dialog renders one text field per registered param (prefilled with
  its default; required ones enforced). The mock `DataSourceFetcher` derives its CSV
  window from parseable ISO-8601 values under common keys (`startDate`/`start`/`from`,
  `endDate`/`end`/`to`) when present, else falls back to a default last-24h window.
  (The earlier typed `Instant` window + MUI `DateTimePicker` approach was replaced by
  this generic query-parameter model.)
- **Filtering (v1)** — column/property selection + row limit only (no value predicates).
  The applied filter is persisted on the execution as `filterSpec`. Filtered data feeds
  the model end-to-end for **GeoJSON**; CSV → model is deferred (mock components consume GeoJSON).
- **Mock components** — `/mock-components/air-quality` and `/traffic` return GeoJSON
  FeatureCollections. A `demo` Spring profile auto-registers them on startup.
- **Ops endpoints** — custom `/health` and `/info` (no Spring Actuator).
- **Docker** — Java 25 base images; the frontend nginx proxies `/api/` → backend
  (same-origin, no CORS), mirroring the Vite dev proxy.

## Constraints deliberately deferred (MVP scope)

No authentication, PostGIS, workflow chaining, or message queues. (Auth is a planned
backlog epic; PostGIS/predicate-filtering/real-fetch are backlog too.)

## Backlog / where work is tracked

Planned work lives in the **"Master Thesis" GitHub Project (v2)** on github.com
(`RadoSlav220/Master-Thesis`), as epics with sub-issues (Status/Priority/Size fields).
Notable next-up epics: real external API fetch (replace the mock), authentication &
authorization, a Dashboard enhancement, and a `DATABASE` data-source type. Flyway
migrations are backlog but **lowest priority** (see Persistence above).
To interact with this repo's GitHub via `gh`, use `GH_HOST=github.com` (the CLI is also
logged into github.tools.sap, which is the default host).

## Conventions

- Backend: mirror existing entity/repository/DTO/service/controller patterns; reuse
  `NotFoundException` and the `GlobalExceptionHandler`.
- Frontend: MUI `Stack`/`Typography` need an `sx` prop present (an overload-resolution
  quirk in the installed MUI version) — route layout props through `sx`.
- Backend → Python calls: build multipart with `LinkedMultiValueMap` + `HttpEntity`/
  `ContentDisposition` on an HTTP/1.1-pinned `RestClient` (see the HTTP-client decision above).
