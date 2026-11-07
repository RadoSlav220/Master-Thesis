# GraFlex Integration — Progress Checklist

> **Update (2026-11-07) — post-integration cleanup; the platform is now metadata-only.**
> Three follow-ups landed since the Upload slice: **(1) the dual-write was removed** — the
> local `stations`/`measurements` tables, their JPA entities/repositories, and the dual-write
> in `uploadStations()` are gone; `StationGeoJsonBuilder` (component execution) and
> `StationCsvZipBuilder` (download) now read from GraFlex over HTTP via the stored entity
> pointers, so station/measurement rows live **only** in GraFlex. **(2) The DataSource /
> live-fetch path was deleted outright** (backend + frontend): the `data_sources` /
> `data_source_query_parameters` tables, the `DataSource*` classes, `DataSourceFetcher`, the
> `/data-sources` API and UI, plus the legacy `Dataset` columns `content`/`analysisResult`/
> `datasetType`/`sourceId` and the `ApiProvenance`/`DatabaseProvenance` variants —
> `datasetOrigin` is now `UPLOAD`-only, provenance is `UploadProvenance`-only. **(3) The mock
> components** (Air Quality / Traffic) and the `demo`-profile seeder were removed; the backend
> boots with **no components**. So B4's "dual-write local tables" no longer applies and C1 is
> fully done (upload + all read-backs go through GraFlex). **Upload error paths verified on the
> live stack (2026-11-07):** missing name/mapping/files → 400; and data-level failures (no
> coordinates, blank coords, missing TIMESTAMP with measurements, ragged CSV) → clean 400s with
> specific detail messages, leaving **no orphaned GraFlex state** — those failures are caught in
> GraFlex's reshape/validation phase *before* any schema is authored or imported. So the
> remaining **atomicity gap (D1)** is narrow: it can only bite on a failure *mid-import* (e.g.
> the measurement import failing after the station import already committed), not on validation.
>
> **Update (2026-10-25) — the Upload slice landed, with a design pivot.** The original plan
> had the **platform** author GraFlex schemas and send two pre-shaped CSVs. We instead moved
> **all reshaping into GraFlex**: the platform forwards the *raw* uploaded CSVs + the
> column-role mapping to a new GraFlex endpoint `POST /datasets/station-upload`, and GraFlex
> does the dedup / long→wide pivot / schema authoring / import itself (data processing stays
> on GraFlex's side of the HTTP boundary). The platform stores the mapping + a GraFlex entity
> pointer in `UploadProvenance`, re-points the station-map reads to GraFlex
> (`/datasets/{entity}/stations|measurements|stats`), and **dual-writes** the local
> `stations`/`measurements` tables so component execution keeps working for now. (Superseded by
> the 2026-11-07 note above — the dual-write has since been removed.) The Python
> `data-analysis-service` is **deleted** (C2 done). Still open: the cleaning/EDA slice
> (A3–A6, B5, B6) and making the upload endpoint **atomic** (it currently commits per step).
> Consequences: workstream-B tasks B2/B3/B4 are satisfied (B3 schema-authoring moved into
> GraFlex); the A2 row is now `POST /datasets/station-upload` (reshape+import), not the
> "platform sends two schemas" shape originally written.

Tracks the **Upload + Clean** integration between the orchestration platform
(`Project/`) and GraFlex (`graflex/`). See
[`graflex-upload-cleaning-proposal.md`](./graflex-upload-cleaning-proposal.md) for the
design and the HTTP contract (§9).

GitHub tracking: epic **RadoSlav220/Master-Thesis#91**, sub-issues #92–#104, on the
"Master Thesis" project board. The GitHub issues are the source of truth for status; this
table is the at-a-glance view. (B2/B3/B4 are grouped as one issue #101; C1/C2 as #104.)

**Scope:** Upload + Clean only. Features / splitting / topology / training stay inside
GraFlex and are **not** platform-driven in this phase.

**Status legend:** ☐ not started · ◐ in progress · ☑ done

---

## Workstream A — GraFlex grows its HTTP API (`graflex/`)

Each endpoint is a thin FastAPI router over an **existing** `graflex.services.*`
function — no new analysis logic.

| ID | Task | Endpoint / deliverable | Wraps (GraFlex fn) | Blocked by | Issue | Status |
|----|------|------------------------|--------------------|------------|-------|--------|
| A1 | FastAPI skeleton + DB session wiring | ASGI app in `src/graflex/api/`, per-request `Session` dep, uvicorn entry | — | — | #92 | ☑ |
| A2 | Upload (reshape + import) | `POST /datasets/station-upload` (raw CSVs + mapping → reshape, author 2 schemas, import). **Non-atomic** — commits per step | `save_entity_schema` ×2 + `import_csv_text` ×2 + new reshaping | A1 | #93 | ☑ (non-atomic) |
| A3 | List cleaning steps | `GET /cleaning-steps` | enumerate `cleaning_steps` registry + param schema | A1 | #94 | ☐ |
| A4 | Cleaning preview | `POST /entities/{entity}/clean/preview` | `preview_clean` (writes nothing) | A1 | #95 | ☐ |
| A5 | Cleaning commit | `POST /entities/{entity}/clean` | `save_cleaning_config` + `clean` | A1 | #96 | ☐ |
| A6 | EDA (data-quality view) | `POST /entities/{entity}/eda` | `run_eda` | A1 | #97 | ☐ |
| A7 | Read versions | `GET /datasets/{entity}/stations`, `.../measurements`, `.../stats` (station-map read-back) | `read_version` | A1 | #98 | ☑ (map reads; generic version read deferred) |
| A8 | Dockerize + compose | GraFlex `Dockerfile` + unified parent-level `docker-compose.yml` | — | — | #99 | ☑ |

## Workstream B — Platform points at GraFlex (`Project/` backend)

| ID | Task | Deliverable | Blocked by | Issue | Status |
|----|------|-------------|------------|-------|--------|
| B1 | Persist upload mapping | `UploadProvenance` carries the column-role mapping + renames + the GraFlex station/measurement entity ids + versions + project id; nullable columns | — | #100 | ☑ |
| B2 | `GraFlexClient` | New `integration/GraFlexClient`; HTTP/1.1-pinned `RestClient` + `LinkedMultiValueMap` multipart; `createDataset` + `readStations`/`readMeasurements`/`readStats` | — | #101 | ☑ |
| B3 | Two-schema authoring | Done **inside GraFlex** (`schema_builder.py`): roles → `SemanticRole`; static station schema (`spatial`) + wide timeseries measurement schema (`event_time` + `references`) | — | #101 | ☑ (moved into GraFlex) |
| B4 | Re-point upload flow | `DatasetService.uploadStations()` → `GraFlexClient.createDataset()`; store entity+version pointer + mapping. (Dual-write removed 2026-11-07 — all reads go to GraFlex) | B1, B2, B3, A2 | #101 | ☑ |
| B5 | Backend cleaning flow | Endpoints + service for list → preview → commit → EDA; station map reads GraFlex static entity via `read_version` | B2, A3, A4, A5, A6, A7 | #102 | ☐ |
| B6 | Frontend cleaning UI | Dynamic step dialog, preview/adjust loop, before/after EDA panels (scope TBD) | B5 | #103 | ☐ |

## Workstream C — Cutover

| ID | Task | Deliverable | Blocked by | Issue | Status |
|----|------|-------------|------------|-------|--------|
| C1 | End-to-end verification | Upload lands in GraFlex DB over HTTP; all reads (map, execution, download) read from GraFlex | A8, B4, B5 | #104 | ☑ (upload + all read-backs via GraFlex; error paths verified; cleaning deferred) |
| C2 | Retire `data-analysis-service` | Removed module + compose entry + `DatasetAnalysisClient` usages (also removed the `/datasets/upload` + `/datasets/{id}/analyze` endpoints) | C1 | #104 | ☑ |

---

## Entry points & critical path

- **Unblocked now (parallel):** A1, A8, B1, B2, B3
- **Critical path:** A1 → A2 → B4 → C1
- **Second slice (cleaning):** A1 → {A3, A4, A5, A6, A7} → B5 → B6

## Decisions to settle (gate specific tasks, not the whole effort)

| # | Decision | Gates | Source |
|---|----------|-------|--------|
| D1 | Partial-import policy: commit-good-rows vs. roll-back vs. threshold | A2, B4 | proposal §10 Q3 |
| D2 | Confirm **generic per-schema import** is the ingestion target (not GraFlex's fixed Sofia "collection" tables) | A2, B4 | proposal §10 Q1 |
| D3 | Mapping storage shape: `UploadProvenance` JSON vs. dedicated column/table | B1 | proposal §4, Step 3b |
