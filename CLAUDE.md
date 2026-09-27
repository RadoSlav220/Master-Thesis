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
  analysis). Uses ruff + pytest.
- `docker-compose.yml` (postgres + backend + frontend + data-analysis-service),
  `.env.example`, `sample-data/` (Sofia sample CSVs; the two `.geojson` files there are now
  legacy — GeoJSON is no longer an accepted upload format, see the CSV-only decision below).
- Each subproject has its **own `.gitignore`** (language/build rules); the root
  `.gitignore` holds only cross-cutting rules (OS/editor/env/secrets).
- **CI** (`.github/workflows/build.yml`) runs three jobs on push: `build-backend`
  (`mvn package`), `build-frontend` (lint + build), `build-analysis-service`
  (ruff + pytest).

## The pipeline (the thesis story)

**Manual CSV upload** of station + measurement data → **auto-analyze + persist** → **map** the
dataset's measurements to a **component**'s expected measurements → run the analytical component →
inspect the component's GeoJSON result. Analytical models are **mocked** for now, and
**components are predefined** (users cannot register them). (Result *visualization* is deferred:
the Map tab was removed — issue #84/#85 — until the model output shape is decided; results are
shown as raw JSON for now. GeoJSON remains only as the component input/output format, not a map view.)

The **primary ingestion path is manual upload** (mentor meeting 2026-08-22): the user uploads
one or more **CSV** files describing measuring stations (at minimum `stationId`, `latitude`,
`longitude`) plus their measurements, and classifies each column's role (station vs.
measurement) during upload. The earlier "register a Data Source → fetch a snapshot over
HTTP" path (API/DATABASE sources) is **kept but parked**, not deleted — see the deferred-
scope note below.

## Key decisions and the "why" (read before changing related code)

- **Orchestration boundary** — data processing (structure analysis, station extraction) is
  delegated to the Python service; the backend never does it itself. Preserve this separation.
- **`data-analysis-service` → graflex (planned direction; 2026-09-26)** — the current
  `data-analysis-service` (a Pandas structure/extraction stub) is slated to be **superseded by
  graflex**, a separate, self-contained data platform (a sibling repo, not in this tree) that runs
  the full pipeline — schema → import CSV → EDA → clean → features → split → topology → train — with
  its own Postgres. **Do not build new work on `data-analysis-service`.** This is
  **supersede-then-delete**, not replace-now: `data-analysis-service` stays the working path until
  graflex grows an HTTP API (it currently has none — notebook-driven today) and the flow is
  re-pointed and verified. Integration shape: **two databases** — the platform DB (Spring/Hibernate)
  and graflex's own DB — with Spring reaching graflex **over HTTP only, never by direct SQL** (to
  avoid coupling to graflex's dynamically-named per-entity tables). This aligns with the supervisor's
  meeting insights: the Python service must run standalone with DB access, and each dataset's data
  lives in its own table with write access — both of which graflex already satisfies natively.
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
- **Station-based CSV upload (primary ingestion path)** — the near-term way data enters the
  platform (mentor meeting 2026-08-22). The user uploads **one or more CSV files** (CSV only —
  no GeoJSON in this flow) that must carry **station** info (at least `stationId`, `latitude`,
  `longitude`) alongside measurements, and assigns each column a **role** during upload
  (`STATION_ID` / `LATITUDE` / `LONGITUDE` / `STATION_ATTRIBUTE` / `MEASUREMENT` / `IGNORE`)
  via an **interactive per-column UI** (client-side header parse + name-based guesses the user
  can correct). The backend validates the required station roles, then transforms the CSVs into
  the dataset's stored representation — GeoJSON point features per station (coords → geometry,
  attributes/measurements → properties) — and runs the existing auto-analyze + persist step.
  GeoJSON here is only the **internal storage/consumption** format (what the map + components
  already use); it is **not** an accepted upload format. The existing generic single-file
  upload path (`/datasets/upload`) is **CSV-only** (issue #86/#87 removed GeoJSON as an accepted
  dataset type) and coexists with this station-based flow.
  Rationale for choosing upload over live fetch: real sensor APIs split station metadata and
  measurements across separate endpoints (would force users to describe a whole fetch pipeline),
  and HTTP fetching is fragile — too complex for now. Tracked as epic #63 (sub-issues #64/#65/#66).
  During mapping the user may also give any **MEASUREMENT** column a **canonical name** (default =
  the header), so differently-named columns for the same quantity (`pm25` vs `PM2.5`) unify into a
  single `measurementType`. This travels as an optional `renames` map (`{filename: {column: name}}`)
  alongside the role `mapping`, applied in the Python extractor; measurements are deduplicated by
  `(stationExternalId, timestamp, measurementType)` with **last-value-wins** on a collision. Issue #79.
  **Download** (`GET /datasets/{id}/download`): a station-based dataset has no `content`, so it is
  reconstructed in the backend (`StationCsvZipBuilder`) as a **`.zip` of `stations.csv` +
  `measurements.csv`** — measurements **wide** (one column per `measurementType`), headers named so
  the archive round-trips back through Create Dataset. Content-based datasets still download as a
  single CSV file. Issue #72.
- **Components are predefined** — users **cannot** register or edit analytical components
  (mentor meeting 2026-08-22). Predefined components are **seeded** (the `demo` profile
  auto-registers the mocks; real ones arrive via the "real components" epic). The user-facing
  "Register Component" flow (frontend `ComponentForm` + backend create/update endpoints) is
  slated for **removal** (issue #67); listing/viewing and **executing** components stay.
- **Persistence** — Hibernate `ddl-auto: update`. Known limitations: it cannot add NOT
  NULL columns to populated tables, and column renames/removals leave orphaned columns
  behind. Add new columns **nullable**. `@Enumerated(STRING)` enums get a generated CHECK
  constraint on their column. In early dev, the local Postgres volume (`pgdata`) can just
  be pruned to shed accumulated schema drift. Flyway/migrations are the **lowest-priority**
  backlog item — do not design around them or treat them as a prerequisite.
- **DataSource / API-fetch model (parked — and now likely to be REMOVED)** — the "register a
  source → fetch a snapshot over HTTP" mechanism is **deprioritized in favor of manual CSV upload**
  (mentor meeting 2026-08-22). **Update (2026-09-07): the maintainer expects the entire API-fetch
  path to be out of scope for the thesis MVP and will probably delete all of its logic.** Do NOT
  build new work on top of it, and do NOT anchor the DATABASE-source design on it (see the DATABASE
  bullet below — that design deliberately reuses the station-upload pipeline instead). The
  description below records how the API path works today, for reference until it is removed.
  `type` is a `DataSourceType` **enum** = `API` (with `DATABASE`
  reserved for later) + an `outputFormat` that is now **CSV-only** (issue #86/#87 removed the
  `GEOJSON` output format and the two GeoJSON demo sources — USGS/Geoapify — from the `demo`
  bootstrap; `DataSourceFetcher`'s mock generates CSV only). At registration a source
  also declares the **query parameters** its API expects (name, `required` flag, optional
  `defaultValue`). These definitions are stored **relationally** as a
  `@ElementCollection<QueryParameterDefinition>` (table `data_source_query_parameters`,
  FK `data_source_id`), not a JSON blob — chosen so they're queryable and Hibernate-managed.
  The `QueryParameter` record stays in `dto/` as the wire contract. Fetching a snapshot is
  mocked by `DataSourceFetcher` (no real HTTP yet). Future `DATABASE` sources will need
  type-specific config (connection/query), likely a `DataSourceConfig` sealed hierarchy
  mirroring `DatasetProvenance` below — not yet built.
- **Fetch is parameterized by the source's registered query parameters** *(part of the parked
  fetch path above)* — the fetch request carries only a dataset `name` + a `Map<String,String> queryParameters` (values
  for the source's registered params). There are **no dedicated start/end date fields**:
  a time window, if a source needs one, is just registered as ordinary query parameters.
  The frontend Fetch dialog renders one text field per registered param (prefilled with
  its default; required ones enforced). The mock `DataSourceFetcher` derives its CSV
  window from parseable ISO-8601 values under common keys (`startDate`/`start`/`from`,
  `endDate`/`end`/`to`) when present, else falls back to a default last-24h window.
  (The earlier typed `Instant` window + MUI `DateTimePicker` approach was replaced by
  this generic query-parameter model.)
- **DATABASE data-source fetch — design direction (planned; epic #37, parked; UNRESOLVED — needs
  supervisor input)** — a relational DB source produces **rows in tables**, which *when flat* is the
  same shape the station-CSV upload consumes (tabular rows + a column-role mapping). Tentative
  direction (2026-09-07): converge on the existing station-extraction pipeline ("a DB fetch is an
  upload where the platform generates the rows"); **do NOT build on the API-fetch mechanism above**
  (slated for deletion); the user **picks a table + columns** and the platform **builds a bounded,
  parameterized `SELECT`** (**NOT** free-form user-typed SQL — arbitrary SQL against a
  stored-credential connection is a security liability, especially with no auth yet); selected
  columns feed the **same role-mapping step** as CSV upload
  (`STATION_ID`/`LATITUDE`/`LONGITUDE`/`TIMESTAMP`/`STATION_ATTRIBUTE`/`MEASUREMENT`/`IGNORE`); rows
  → relational `stations`/`measurements` via the existing extraction path (ideally generate an
  in-memory CSV and reuse `/extract-stations`, one extraction implementation).
  **KNOWN PROBLEM with this approach (2026-09-07):** the station pipeline joins **only on
  `stationId`** and assumes a **denormalized/flat** input where every table/file carries `stationId`.
  Real DB schemas are **normalized**, and station linkage may be **transitive across an FK chain**,
  not a direct column. Counterexample: `stations`, `measurements_metadata`, `measurements_values`
  where `measurements_values` keys off `measurement_id` → `measurements_metadata` → `station_id`.
  "Pick table + columns, join by stationId" **cannot express that** — there is no `stationId` in
  `measurements_values` to map. **Note the CSV path has the *same* limitation** — it also only
  joins on `stationId` and assumes flat input — but it **externalizes the flattening**: a human
  produced the CSV (ran the export/join) *before* upload, so the platform never sees the normalized
  schema. A DB fetch reaches into the **live normalized schema**, so the flattening becomes the
  **platform's** job at fetch time. That leaves two real options: **(1) push flattening back onto the
  user** — require them to supply a query/view returning flat `stationId`-bearing rows (i.e. the CSV
  posture expressed as SQL/a view — which reopens the raw-SQL security question); or **(2) build
  join-specification machinery** in the platform (a guided join-builder) — a genuinely bigger feature,
  justified only if the real schemas demand it. Which option is right **depends on the shape of the
  actual databases, which is unknown** → to be settled with the supervisor. Bring these questions:
  how normalized are the target DBs? is station linkage always a direct column or transitive via FKs?
  do all relevant tables even have a station key? Other still-open items (defer): real JDBC vs. a
  mocked demonstrator (mock-first favored); where mapping is authored (interactive-at-fetch reusing
  `DatasetForm`'s mapping UI vs. stored on the source); config as a `DatabaseConfig` variant of the
  anticipated `DataSourceConfig` sealed hierarchy.
- **Measurement mapping** — each predefined component declares a set of **expected measurements**
  (`Component.expectedMeasurements`, seeded in the `demo` profile). When running a component against a
  **station-based** dataset, the user maps the dataset's measurement columns onto the component's
  expected names; execution is **blocked (400, `InvalidExecutionException`)** if any expected
  measurement is unmapped. The chosen mapping is persisted on the execution as `measurementMapping`
  (JSON provenance). For station-based datasets `StationGeoJsonBuilder` then synthesizes a GeoJSON
  FeatureCollection embedding each mapped measurement's **full time series** in feature properties.
  (This replaced the earlier v1 filter — column/row selection — which was redundant once mapping
  governs what reaches the model.)
- **Mock components** — `/mock-components/air-quality` and `/traffic` return GeoJSON
  FeatureCollections. A `demo` Spring profile auto-registers them on startup. (Components are
  **predefined/seeded**, not user-registered — see the "Components are predefined" decision above.)
- **Ops endpoints** — custom `/health` and `/info` (no Spring Actuator).
- **Docker** — Java 25 base images; the frontend nginx proxies `/api/` → backend
  (same-origin, no CORS), mirroring the Vite dev proxy.

## Constraints deliberately deferred (MVP scope)

No authentication, PostGIS, workflow chaining, or message queues. (Auth is a planned
backlog epic; PostGIS/predicate-filtering are backlog too.)

**Parked in favor of manual CSV upload (mentor meeting 2026-08-22):** the live-fetch data-source
mechanism — real external **API** fetch (epic #13) and the **DATABASE** (JDBC) source type
(epic #37) — is kept in the codebase but deprioritized (moved to P2/Backlog). Do not build new
work on top of it or treat it as the primary ingestion path; manual station-based CSV upload
(epic #63) is the focus.

## Backlog / where work is tracked

Planned work lives in the **"Master Thesis" GitHub Project (v2)** on github.com
(`RadoSlav220/Master-Thesis`), as epics with sub-issues (Status/Priority/Size fields).
Current focus: **station-based CSV upload** (epic #63) and removing the **Register Component**
flow (#67). Other next-up epics: authentication & authorization, a Dashboard enhancement, and
real analytical components (replace the mocks). The live-fetch epics — real external API fetch
(#13) and the `DATABASE` data-source type (#37) — are **parked** (see the deferred-scope note
above). Flyway migrations are backlog but **lowest priority** (see Persistence above).
To interact with this repo's GitHub via `gh`, use `GH_HOST=github.com` (the CLI is also
logged into github.tools.sap, which is the default host).

## Conventions

- Backend: mirror existing entity/repository/DTO/service/controller patterns; reuse
  `NotFoundException` and the `GlobalExceptionHandler`.
- Frontend: MUI `Stack`/`Typography` need an `sx` prop present (an overload-resolution
  quirk in the installed MUI version) — route layout props through `sx`.
- Backend → Python calls: build multipart with `LinkedMultiValueMap` + `HttpEntity`/
  `ContentDisposition` on an HTTP/1.1-pinned `RestClient` (see the HTTP-client decision above).
