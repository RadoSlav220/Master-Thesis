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
- `docker-compose.yml` — **platform-only** stack (postgres + backend + frontend); the
  backend needs an external GraFlex (`GRAFLEX_SERVICE_URL`). A **unified** compose that
  also runs GraFlex + its Postgres lives one level up (`../docker-compose.yml`, parent of
  this repo and the `graflex` sibling repo).
- `.env.example`, `sample-data/` (Sofia sample CSVs; the two `.geojson` files there are now
  legacy — GeoJSON is no longer an accepted upload format, see the CSV-only decision below).
- **GraFlex** (the data platform) is a **separate sibling repo** (`../graflex`), not in
  this tree. The backend drives it over HTTP.
- Each subproject has its **own `.gitignore`** (language/build rules); the root
  `.gitignore` holds only cross-cutting rules (OS/editor/env/secrets).
- **CI** (`.github/workflows/build.yml`) runs two jobs on push: `build-backend`
  (`mvn package`) and `build-frontend` (lint + build).

## The pipeline (the thesis story)

**Manual CSV upload** of station + measurement data → **GraFlex reshapes + imports + stores**
→ **map** the dataset's measurements to a **component**'s expected measurements → run the
analytical component → inspect the component's GeoJSON result. The former **mock** analytical
components were removed (2026-11-07), so **no components are registered by default** — real
ones arrive via the "real components" epic; **components are predefined** (users cannot register
them). (Result
*visualization* is deferred: the Map tab was removed — issue #84/#85 — until the model output
shape is decided; results are shown as raw JSON for now. GeoJSON remains only as the
component input/output format, not a map view.)

The **sole ingestion path is manual station upload** (mentor meeting 2026-08-22): the user
uploads one or more **CSV** files describing measuring stations (at minimum `stationId`,
`latitude`, `longitude`) plus their measurements, and classifies each column's role during
upload. The backend forwards the raw files + mapping to **GraFlex**, which reshapes and
imports them; the platform stores only metadata (the mapping + a GraFlex entity pointer).
The earlier "register a Data Source → fetch a snapshot over HTTP" path (API/DATABASE
sources) has been **removed entirely** (2026-11-07) — see the DataSource-removal note below.
(The old standalone single-CSV `/datasets/upload` and the `/datasets/{id}/analyze`
structure-analysis endpoint were **removed** when the Python analysis service was retired — see
below.)

## Key decisions and the "why" (read before changing related code)

- **Orchestration boundary** — all data processing (reshaping, deduplication, pivoting,
  schema authoring, import, and planned cleaning/EDA) is delegated to GraFlex over HTTP; the
  backend never does it itself. Preserve this separation.
- **`data-analysis-service` → graflex (DONE; retired 2026-10-25)** — the former
  `data-analysis-service` (a Pandas structure/extraction stub) has been **superseded by
  graflex** and **deleted**. GraFlex is a separate, self-contained data platform (a sibling
  repo, `../graflex`) that runs the pipeline — schema → import CSV → EDA → clean → features →
  split → topology → train — with its own Postgres. The station-upload flow now calls GraFlex's
  `POST /datasets/station-upload` (reshape + author two schemas + import), and the station-map
  read-back reads GraFlex over HTTP (`/datasets/{entity}/stations|measurements|stats`). The
  retirement removed `DatasetAnalysisClient`, the plain `/datasets/upload`, and
  `/datasets/{id}/analyze`. Integration shape: **two databases** — the platform DB (Spring/Hibernate)
  and graflex's own DB — with Spring reaching graflex **over HTTP only, never by direct SQL** (to
  avoid coupling to graflex's dynamically-named per-entity tables). This aligns with the supervisor's
  meeting insights: the Python service must run standalone with DB access, and each dataset's data
  lives in its own table with write access — both of which graflex already satisfies natively.
- **Backend → GraFlex HTTP client** — `GraFlexClient` uses a Spring `RestClient` **pinned
  to HTTP/1.1** (`JdkClientHttpRequestFactory` with `HttpClient.Version.HTTP_1_1`). The JDK
  client's default HTTP/2 upgrade corrupts multipart requests to uvicorn (422 / "invalid HTTP
  request"). Do **not** switch to WebClient — the project deliberately has no webflux.
  Multipart parts are built with `LinkedMultiValueMap` + `HttpEntity`/`ContentDisposition`,
  **not** `MultipartBodyBuilder` (which pulls in reactive-streams that aren't on the classpath).
  (`DatasetAnalysisException`/`InvalidDataException` are reused by `GraFlexClient` for
  service-failure vs. bad-data cases — kept despite the analysis service's retirement.)
- **Dataset storage** — datasets are **station-based**: their rows live in GraFlex, not the
  platform DB. The platform stores **metadata only** — a `datasetOrigin` and a `provenance`
  JSON blob (the user's column-role mapping + the GraFlex entity/version pointer). The `Dataset`
  entity has exactly: `id`, `name`, `type`, `description`, `datasetOrigin`, `provenance`,
  `createdAt`. The legacy `content`/`datasetType`/`analysisResult` columns (analysis-service era)
  and `sourceId` (API-fetch era) were **removed** (2026-11-07) along with the DataSource path.
- **Dataset origin & provenance** — a `datasetOrigin` enum (now **`UPLOAD` only**, since the
  API/DATABASE origins were removed) records where a dataset came from, plus a nullable
  `provenance` JSON column holding the origin-specific detail. The provenance shape is a
  **sealed `DatasetProvenance` hierarchy** — now a single variant, `UploadProvenance` (the
  column-role mapping + renames + the GraFlex station/measurement entity ids and versions +
  project id) — serialized with a Jackson `"type"` discriminator. The sealed-interface +
  discriminator shape is kept (rather than collapsing to a plain record) so re-introducing other
  origins later stays cheap, and so the stored JSON keeps its `"type": "UPLOAD"` tag. (The former
  `ApiProvenance`/`DatabaseProvenance` variants were removed with the DataSource path, 2026-11-07.)
- **Station-based CSV upload (the sole ingestion path)** — the way data enters the
  platform (mentor meeting 2026-08-22). The user uploads **one or more CSV files** (CSV only —
  no GeoJSON in this flow) that must carry **station** info (at least `stationId`, `latitude`,
  `longitude`) alongside measurements, and assigns each column a **role** during upload
  (`STATION_ID` / `LATITUDE` / `LONGITUDE` / `TIMESTAMP` / `STATION_ATTRIBUTE` / `MEASUREMENT` /
  `IGNORE`) via an **interactive per-column UI** (client-side header parse + name-based guesses the
  user can correct). `DatasetService.uploadStations()` forwards the raw files + mapping to
  **GraFlex** (`GraFlexClient.createDataset()` → `POST /datasets/station-upload`), which reshapes
  (deduplicate stations, pivot measurements wide), authors a static station schema + a wide
  timeseries measurement schema, and imports both into GraFlex's own Postgres. The platform
  persists only the mapping + the GraFlex entity/version pointer (in `UploadProvenance`). All
  station/measurement rows live **only in GraFlex** and are read back over HTTP whenever needed:
  the map reads (`/datasets/{id}/stations|measurements|stats`), component execution
  (`StationGeoJsonBuilder`), and download (`StationCsvZipBuilder`) all go through `GraFlexClient`.
  (An earlier increment dual-wrote the rows into local `stations`/`measurements` tables as a
  bridge; those tables, their entities/repositories, and the dual-write were **removed**
  2026-11-07 once the readers were re-pointed to GraFlex — the platform DB holds no row data.)
  Rationale for choosing upload over live fetch: real sensor APIs split station metadata and
  measurements across separate endpoints (would force users to describe a whole fetch pipeline),
  and HTTP fetching is fragile — too complex for now. Tracked as epic #63 (sub-issues #64/#65/#66).
  During mapping the user may also give any **MEASUREMENT** column a **canonical name** (default =
  the header), so differently-named columns for the same quantity (`pm25` vs `PM2.5`) unify into a
  single `measurementType`. This travels as an optional `renames` map (`{filename: {column: name}}`)
  alongside the role `mapping`, applied in GraFlex's reshaping; measurements are deduplicated by
  `(stationExternalId, timestamp, measurementType)` with **last-value-wins** on a collision. Issue #79.
  (The former standalone single-file `/datasets/upload` and the `/datasets/{id}/analyze` endpoint
  were **removed** with the Python analysis service — station upload is the only ingestion path.)
  **Download** (`GET /datasets/{id}/download`): a station-based dataset is reconstructed in the
  backend (`StationCsvZipBuilder`, reading the rows back from GraFlex over HTTP) as a **`.zip` of
  `stations.csv` + `measurements.csv`** — measurements **wide** (one column per `measurementType`),
  headers named so the archive round-trips back through Create Dataset. Issue #72.
- **Components are predefined** — users **cannot** register or edit analytical components
  (mentor meeting 2026-08-22). The two **mock components** (Air Quality / Traffic) and the
  `demo`-profile seeder that auto-registered them were **removed** (2026-11-07), so the backend
  now starts with **no components**; real ones arrive via the "real components" epic. The
  user-facing "Register Component" flow (frontend `ComponentForm` + backend create/update
  endpoints) is slated for **removal** (issue #67); listing/viewing and **executing** components
  stay. (With no components registered and register-component going away, a seeding/provisioning
  path for real components will be needed — tracked by the real-components epic.)
- **Persistence** — Hibernate `ddl-auto: update`. Known limitations: it cannot add NOT
  NULL columns to populated tables, and column renames/removals leave orphaned columns
  behind. Add new columns **nullable**. `@Enumerated(STRING)` enums get a generated CHECK
  constraint on their column. In early dev, the local Postgres volume (`pgdata`) can just
  be pruned to shed accumulated schema drift. Flyway/migrations are the **lowest-priority**
  backlog item — do not design around them or treat them as a prerequisite.
- **DataSource / live-fetch model (REMOVED 2026-11-07)** — the "register a Data Source → fetch a
  snapshot over HTTP" mechanism (both the **API** fetch and the reserved **DATABASE** source type)
  has been **deleted outright**, backend and frontend. Gone: `DataSource`/`DataSourceType`/
  `QueryParameterDefinition`/`QueryParameter`/`DataSourceRequest`/`DataSourceResponse`/
  `FetchDatasetRequest`, `DataSourceRepository`/`Service`/`Controller`, `DataSourceFetcher`,
  `DataSourceFetchException`, and the `data_sources` + `data_source_query_parameters` tables; on the
  frontend the Data Sources page, forms, API module, hook, and nav entry. The maintainer confirmed
  the live-fetch path is out of scope for the thesis MVP (the earlier DATABASE-fetch design — a
  guided join-builder vs. user-supplied flat view, unresolved normalization questions — is dropped
  with it). **Manual station CSV upload is the only ingestion path.** If a DB/API ingestion is ever
  revisited, the intended direction was still "generate flat rows and reuse `POST
  /datasets/station-upload`", not a new parallel mechanism.
- **Measurement mapping** — each component declares a set of **expected measurements**
  (`Component.expectedMeasurements`). When running a component against a
  **station-based** dataset, the user maps the dataset's measurement columns onto the component's
  expected names; execution is **blocked (400, `InvalidExecutionException`)** if any expected
  measurement is unmapped. The chosen mapping is persisted on the execution as `measurementMapping`
  (JSON provenance). For station-based datasets `StationGeoJsonBuilder` then synthesizes a GeoJSON
  FeatureCollection embedding each mapped measurement's **full time series** in feature properties.
  (This replaced the earlier v1 filter — column/row selection — which was redundant once mapping
  governs what reaches the model.)
- **Ops endpoints** — custom `/health` and `/info` (no Spring Actuator).
- **Docker** — Java 25 base images; the frontend nginx proxies `/api/` → backend
  (same-origin, no CORS), mirroring the Vite dev proxy.

## Constraints deliberately deferred (MVP scope)

No authentication, PostGIS, workflow chaining, or message queues. (Auth is a planned
backlog epic; PostGIS/predicate-filtering are backlog too.)

**Removed, not parked (2026-11-07):** the live-fetch data-source mechanism — real external
**API** fetch (epic #13) and the **DATABASE** (JDBC) source type (epic #37) — has been deleted
from the codebase (see the DataSource-removal decision above). Manual station-based CSV upload
(epic #63) is the only ingestion path. Do not re-introduce a DataSource abstraction; if live
ingestion is revisited, generate flat rows and reuse `POST /datasets/station-upload`.

## Backlog / where work is tracked

Planned work lives in the **"Master Thesis" GitHub Project (v2)** on github.com
(`RadoSlav220/Master-Thesis`), as epics with sub-issues (Status/Priority/Size fields).
Recently landed: the **GraFlex integration** (epic #91) — station upload is reshaped +
imported + stored by GraFlex over HTTP, the Python `data-analysis-service` is retired, the
local dual-write was dropped (readers read GraFlex directly), and the **DataSource / live-fetch
path was removed entirely** (2026-11-07). Current focus: GraFlex **cleaning / EDA** (the next
slice of #91) and removing the **Register Component** flow (#67). Other next-up epics:
authentication & authorization, a Dashboard enhancement, and real analytical components (the
mock Air Quality / Traffic components were removed 2026-11-07; the backend now ships with none). The live-fetch epics — real external API fetch (#13) and the `DATABASE` data-source
type (#37) — are **dropped** (the code they described no longer exists; close/park the issues).
Flyway migrations are backlog but **lowest priority** (see Persistence above).
To interact with this repo's GitHub via `gh`, use `GH_HOST=github.com` (the CLI is also
logged into github.tools.sap, which is the default host).

## Conventions

- Backend: mirror existing entity/repository/DTO/service/controller patterns; reuse
  `NotFoundException` and the `GlobalExceptionHandler`.
- Frontend: MUI `Stack`/`Typography` need an `sx` prop present (an overload-resolution
  quirk in the installed MUI version) — route layout props through `sx`.
- Backend → GraFlex calls: build multipart with `LinkedMultiValueMap` + `HttpEntity`/
  `ContentDisposition` on an HTTP/1.1-pinned `RestClient` (see the HTTP-client decision above).
