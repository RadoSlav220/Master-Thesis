# Proposal: Integrating graflex for Data Upload & Cleaning

**Status:** Proposal for discussion
**Date:** 2026-09-26
**Audience:** Thesis supervisor, Lyudmil (graflex author)
**Author:** Radoslav

---

## 1. Purpose

This document proposes how the **orchestration platform** (Spring backend + React frontend)
and **graflex** (the standalone data platform) should work together for the first two stages
of the pipeline the user sees: **uploading a dataset** and **cleaning it**.

It is meant as a starting point for discussion. It states not just *what* to build, but
*why* each choice was made, so we can disagree on specifics without re-deriving the reasoning.

---

## 2. Background: what each system is

**The orchestration platform** (this thesis) is deliberately *not* an analysis tool. It is a
coordination layer: it ingests datasets, lets a user run **prediction models** against them, and
surfaces the results. It performs no data analysis or modelling itself — it drives the system
(graflex) that does.

**graflex** is a complete, self-contained data-and-modelling platform (Lyudmil's separate
repository). It has its own PostgreSQL database and runs a full pipeline over tabular sensor data:

> schema definition → CSV import → EDA → cleaning → feature engineering → splitting → topology → **model training + prediction**

**The prediction models live inside graflex.** graflex both prepares the data *and* trains and runs
the models that produce the three prediction kinds the thesis targets — prediction in **time**
(future values at an existing station), in **space** (values at a new location), and in **space +
time**. This makes graflex the analytical core, not merely a data-prep service.

graflex already runs **standalone** (driven from a notebook today, against its own database),
which matches the supervisor's requirement that the Python data component work independently of
the Spring/React apps.

**Key consequence:** graflex is far more than the current `data-analysis-service` (a small Pandas
stub that only inspects CSV structure and extracts stations). The plan below assumes graflex will
**supersede** `data-analysis-service` — see §8.

---

## 3. The core architectural principle

> **The platform authors the schema and owns the user experience.
> graflex owns data storage, data processing, and the prediction models.
> The two communicate over HTTP only — the platform never touches graflex's database directly.**

Everything below follows from this principle. The reason for the HTTP-only boundary is explained
in §7.

---

## 4. Architecture at a glance

![Component architecture diagram](./architecture.png)

The analytical/prediction models live **inside graflex** (graph + baseline forecasters). The Spring
backend runs a prediction by calling graflex over HTTP — it does **not** host the models itself.
(The platform keeps a generic "external REST component" mechanism that could host a future model
outside graflex, but the thesis models are graflex-internal.)

### Components and why each exists

| Component | Why it is there |
|---|---|
| **React Frontend** | The only thing the user touches. Owns the upload wizard, the column-role mapping UI, and the cleaning dialog. Holds *no* analytical or storage logic — it just calls the backend. |
| **Spring Boot Backend** | The **orchestration layer** — the thesis contribution. It coordinates everything but analyses nothing itself: it authors graflex schemas from the user's column mapping, drives graflex over HTTP (including asking it to run a prediction), and records what happened. |
| **Platform DB** (PostgreSQL) | The backend's own store: users (later), registered components, execution records, and **dataset metadata** — including a pointer to the corresponding graflex entity + version. It does **not** hold the dataset rows themselves. |
| **graflex** | The **data platform *and* the model host** — does the actual data work (import, cleaning, features) **and trains + runs the prediction models** (the graph and baseline forecasters that produce the temporal / spatial / spatio-temporal predictions). It is a separate, self-contained system that already runs standalone against its own database, satisfying the supervisor's requirement that the data component work independently of the Spring/React apps. |
| **graflex DB** (PostgreSQL) | graflex's own store: entity schemas, saved configs (cleaning recipes, etc.), the actual dataset rows in per-entity `raw` / `cleaned` / `features` tables, and the trained-model / run records. Only graflex connects to it. |
| **Analytical Components (external, optional)** | The platform keeps a generic mechanism for running a model that lives *outside* graflex, behind a REST endpoint (this is what the current mock components use). **The thesis prediction models do NOT use this path — they live inside graflex.** This mechanism stays available for a hypothetical future model hosted elsewhere, so the platform is not locked to a single model host. |

### Communication methods (the arrows)

| From → To | Method | Purpose |
|---|---|---|
| Frontend → Spring backend | **HTTPS / REST, JSON** (browser calls `/api`, nginx-proxied) | All user actions: upload, map columns, request cleaning, run a prediction, view results. |
| Spring backend → Platform DB | **JDBC / SQL** (Hibernate) | Persist users, components, executions, dataset metadata. |
| Spring backend → graflex | **HTTP, JSON** (+ `multipart` for the CSV upload) | Save a schema, import a CSV, preview/run cleaning, read versions, **and run a prediction** — the contract in §9. |
| Spring backend → external component *(optional)* | **HTTP, JSON** | Only for a model hosted *outside* graflex. Unused by the thesis models (which are graflex-internal). |
| graflex → graflex DB | **JDBC / SQL** (SQLAlchemy) | graflex reads and writes its own schemas, configs, data tables, and model/run records. |

**The one boundary that is deliberately absent:** there is **no arrow from the Spring backend to
the graflex DB.** The backend reaches graflex's *data* only by asking graflex over HTTP — never by
opening a SQL connection to graflex's database. §7 explains why this matters.

---

## 5. Proposed flow — Data Upload

The user uploads one or more station CSV files and tells the platform what each column means; the
platform turns that into a graflex **entity schema** and imports the rows into graflex.

### Step 1 — Upload CSV file(s) in the UI
The user selects one or more CSV files. (Already implemented.)

### Step 2 — The browser reads the headers and pre-guesses each column's role
This happens **entirely in the browser** — no server call. The frontend already reads the CSV
header line and guesses a role for each column (e.g. a column named `lat` is guessed as latitude).

> **Why client-side, not via graflex?** This already works today and needs no network round-trip.
> graflex's job begins when we save a schema, not when we read a header line. Sending files to
> graflex just to list their columns would add complexity for no benefit.

### Step 3 — The user confirms column roles and (optionally) data quality hints
For each column the user assigns a **role**:

| Platform role | Meaning |
|---|---|
| `STATION_ID` | The station/point identifier |
| `LATITUDE` / `LONGITUDE` | The station's location |
| `TIMESTAMP` | The time axis of a reading |
| `MEASUREMENT` | A measured quantity (PM2.5, NO₂, …) |
| `STATION_ATTRIBUTE` | Descriptive station info (name, type, …) |
| `IGNORE` | Not imported |

The user may **optionally** also set, per measurement column:
- its **data type** (defaulted sensibly — measurements→number, timestamp→timestamp, ids→text),
- a **valid range** (e.g. PM2.5 ∈ [0, 500]),
- **missing-value sentinels** (e.g. `-999` means "no reading", common in sensor exports).

> **Why optional?** Requiring a valid range and sentinels for every column would make uploading
> tedious. These are *data-quality hints* that the cleaning stage later uses; sensible defaults
> (no range, no sentinels) let a user upload quickly, while power users can be precise.
>
> **Why capture sentinels at all?** Many sensor feeds encode "missing" as an out-of-band number
> like `-999` rather than an empty cell. If we don't declare that, downstream cleaning and models
> treat `-999` as a real reading and corrupt every average. graflex's cleaning can convert declared
> sentinels to true nulls — but only if we tell it which values are sentinels.

### Step 4 — The platform builds **two** graflex schemas
A single upload becomes **two** graflex entity schemas, because graflex (sensibly) separates a
station's *fixed location* from its *time-varying readings*:

1. a **static station schema** — the station id, its latitude/longitude, and any attributes;
2. a **timeseries measurement schema** — the station id, the timestamp, and the measured values,
   linked back to the station schema by the station id (a foreign-key reference).

> **Why two schemas?** This mirrors how graflex models the world (a static "station" entity and a
> timeseries "measurements" entity, joined by the station id) and how our own platform already
> stores data (stations separate from measurements). Splitting them keeps each row lean —
> coordinates are not repeated on every reading — and matches graflex's example schemas exactly.

### Step 5 — The platform makes **two** calls to graflex: save the schema, then import
These are two distinct operations with distinct failure modes:

1. **Save schema** — graflex stores the two schemas in its config registry.
2. **Import CSV** — graflex validates the file against the schema and writes the rows into its
   own storage, returning an **import report**: refused outright if a required column is missing;
   otherwise a per-row summary of anything that could not be parsed.

> **Why two steps, not one "store"?** A schema can save successfully and the import still be
> refused (e.g. the file is missing a required column). Keeping them separate means the user gets
> a precise, actionable error ("import refused: no `timestamp` column") instead of a vague failure.
> The import report should be shown in the UI.

---

## 6. Proposed flow — Data Cleaning

A dedicated **Cleaning** area lets the user turn raw imported data into a cleaned version.

### Step 1 — A Cleaning tab/section
Operates on the dataset's *raw* version and produces a *cleaned* version. graflex keeps these as
separate stored versions (raw, cleaned, features) — cleaning never destroys the original.

### Step 2 — The user picks cleaning steps and their settings in a dialog
graflex offers a set of cleaning steps — e.g. *replace missing-value sentinels with null*, *fill
missing values* (median / forward-fill / constant), *enforce valid ranges* (clip / null / drop),
*drop duplicate rows*, *handle outliers*, and more. Each step has its own settings.

> **Recommendation:** graflex should expose a "list of available cleaning steps and their settings"
> so the dialog can be **built dynamically** from what graflex actually supports, rather than the
> frontend hardcoding each step's form. If that is too much for a first version, we start with a
> small curated set of steps and expand later.

### Step 3 — Preview before committing
Before writing anything, the user sees a **preview**: how many rows/columns each step changes.

> **Why preview?** Cleaning is destructive-looking to a user ("will this delete my data?").
> graflex can run the steps *without writing*, so the user sees the effect first. This turns
> cleaning from "run and hope" into an informed choice.

### Step 4 — Commit: graflex writes the cleaned version; the cleaning recipe is saved
On commit, graflex applies the steps, writes the cleaned version, and **saves the cleaning recipe**
(the ordered list of steps) as a reusable, versioned config.

> **Why save the recipe, not just the result?** Cleaning is iterative — a user will tweak steps and
> re-run. graflex versions the cleaned output each time the recipe changes, so we can compare and
> reproduce. Treating cleaning as a *saved, editable recipe* (not a one-off action) matches how
> graflex already works.

### Step 5 (recommended) — A lightweight data-quality view to guide cleaning
Before cleaning, show a small summary of *what is wrong* with the data: how many values hit a
missing-sentinel, how many are out of range, time gaps, stuck sensors, etc. graflex produces
exactly this (its read-only "exploratory analysis" stage).

> **Why include it?** A user can only choose the *right* cleaning steps if they can *see* the
> problems. Without this, the cleaning dialog asks the user to fix issues they cannot observe.
> A full analysis UI is not needed for a first version, but even a simple "per-column summary +
> problem counts" makes the cleaning step usable — and demonstrates that the platform *understands*
> the data, which is a good thesis talking point.

---

## 7. Why the platform talks to graflex over HTTP, never its database directly

Both the platform and graflex use PostgreSQL. It is tempting to let the platform read graflex's
tables directly. We propose **not** to, and instead go through graflex's HTTP API.

**Reasons:**
- **graflex's data tables are created dynamically**, named after each entity/version (e.g.
  `station_measurements__cleaned`). If the platform queried them directly, it would be tied to
  graflex's internal naming — and any refactor Lyudmil makes to that layout would silently break
  the platform.
- **Clean ownership.** graflex owns its schema and storage; the platform owns its own (users,
  components, executions, dataset metadata). An HTTP boundary keeps these from entangling.
- **It matches how the platform already works** — the backend already calls the existing Python
  service over HTTP, and the "backend never does data processing itself" rule is already in place.

**Resulting two-database picture:**

| Database | Owner | Who connects |
|---|---|---|
| Platform DB | Spring / Hibernate | **Platform only** — users, components, executions, dataset *metadata* |
| graflex DB | graflex | **graflex only** — schemas, configs, raw/cleaned/feature tables |

The platform reaches dataset *data* by asking graflex over HTTP, not by opening a SQL connection to
graflex's database.

### Where execution results are stored (a consequence of this boundary)

Running a prediction produces two related-but-different things, which have different owners:

1. **The execution record** — *"user X ran model Y against dataset Z at time T; status; the
   measurement mapping used."* This is **orchestration provenance** and lives in the **Platform DB**.
   graflex structurally cannot hold it: graflex has no concept of the platform's users, its
   registered components, or its dataset ids — and, crucially, **some executions never reach
   graflex at all** (an invalid mapping rejected with `400`, or graflex being unreachable). Those
   still need an execution row; only the platform can provide it.
2. **The prediction output + the reproducible run** — the predicted values, plus the config / metrics
   / trained model that produced them. graflex **owns this** (it is the system that computed it, and
   its own pipeline needs the run record for reproducibility and re-analysis).

**How they link — reference, don't duplicate.** graflex keeps a `run_id → result` mapping in its own
store. The Platform DB's `executions` table carries a **`graflex_run_id`** column that references
that run. This is a **logical reference resolved over HTTP** (e.g. `GET /runs/{run_id}`), **not** a
SQL foreign key — the two databases cannot join, and nothing enforces integrity across them.

Two rules make this correct:

- **`graflex_run_id` is nullable.** It is populated when graflex actually ran, and null for
  executions that failed before reaching graflex. (This is the second reason the execution record
  must live in the platform.)
- **The platform also caches the result on the execution, for display.** The `graflex_run_id`
  pointer is the source-of-truth link (for audit / regenerate); a **cached copy** of the result on
  the execution row is what the UI shows — so displaying a past execution does not depend on graflex
  being up, and does not re-query graflex on every view. The cached result is safe to keep because a
  completed prediction is **immutable** (the same immutable-snapshot caching the platform already
  uses for fetched-dataset analysis). If graflex produces a new run, that is a **new execution** —
  the old cached result is never mutated in place.

Resulting `executions` shape (platform side):

```
id                (platform UUID — the platform's own execution id)
user_id, component_id, dataset_id, measurement_mapping
status, created_at, finished_at, error_message
graflex_run_id    (nullable — reference to graflex's run; null if it never reached graflex)
result            (nullable — cached copy of graflex's output, for display)
```

> **Open question for graflex (see §10):** for a *prediction* call, what stable id does graflex
> return — a per-prediction `run_id`, or just the underlying trained-model/run id? graflex already
> persists *training* runs; whether an inference call is also persisted-and-addressable determines
> exactly what `graflex_run_id` points at (and, if prediction is stateless, the platform's cached
> `result` becomes the only stored copy of that specific prediction — which is acceptable).

---

## 8. Relationship to the current `data-analysis-service`

The plan assumes graflex **supersedes** the current `data-analysis-service`. Recommended sequencing:

1. graflex grows the HTTP API this plan needs (see §9). `data-analysis-service` keeps working
   throughout.
2. The new upload + clean flow is pointed at graflex, while the old path still functions.
3. Once graflex fully covers ingestion and cleaning, `data-analysis-service` is removed.

> **Why not replace it immediately?** graflex has **no HTTP API yet** (it is notebook-driven
> today). Removing the working service before its replacement exists would leave the platform
> non-functional. "Supersede then delete" keeps the platform working at every step.

---

## 9. What this requires from graflex (the HTTP API to build)

graflex currently has no HTTP layer. This plan needs the following endpoints, each a thin wrapper
over a graflex function that **already exists** (named in the "graflex function" column). This
section is the **contract** between the two workstreams: once agreed, Lyudmil can build the
endpoints while the platform side builds the UI and translation logic in parallel.

| # | Endpoint | graflex function | Used by |
|---|---|---|---|
| 1 | `POST /entities` | `save_entity_schema` | Upload step 4/5 |
| 2 | `POST /entities/{entity}/import` | `import_csv_text` | Upload step 5 |
| 3 | `GET /cleaning-steps` | *(new — enumerate registered steps + param schema)* | Cleaning step 2 |
| 4 | `POST /entities/{entity}/clean/preview` | `preview_clean` | Cleaning step 3 |
| 5 | `POST /entities/{entity}/clean` | `save_cleaning_config` + `clean` | Cleaning step 4 |
| 6 | `GET /entities/{entity}/versions` | `list_versions` | Show data in UI |
| 7 | `GET /entities/{entity}/versions/{version}` | `read_version` | Show data in UI |
| 8 | `POST /entities/{entity}/eda` *(optional)* | `run_eda` | Quality view (cleaning step 5) |

> The shapes below are illustrative and follow graflex's existing data models
> (`EntitySchema`, `CleaningConfig`, `ImportReport`, …). Field names/exact structure are for
> discussion — the point is to make the contract concrete, not final.

### 8.1 `POST /entities` — save an entity schema

A single upload sends **two** of these (the static station entity, then the timeseries measurement
entity that references it).

**Request** (the static station entity):
```json
{
  "id": "sofia_station",
  "temporality": "static",
  "keys": { "primary": ["point_id"] },
  "columns": [
    { "name": "point_id",  "type": "string",  "role": "entity_id", "nullable": false },
    { "name": "name",      "type": "string",  "role": "metadata" },
    { "name": "latitude",  "type": "float64", "role": "spatial",   "nullable": false },
    { "name": "longitude", "type": "float64", "role": "spatial",   "nullable": false }
  ]
}
```

**Request** (the timeseries measurement entity — note the optional quality hints from upload step 3):
```json
{
  "id": "sofia_air_quality",
  "temporality": "timeseries",
  "keys": { "primary": ["point_id", "timestamp"] },
  "columns": [
    { "name": "point_id",  "type": "string",    "role": "entity_id",  "nullable": false,
      "references": "sofia_station.point_id" },
    { "name": "timestamp", "type": "timestamp", "role": "event_time", "nullable": false },
    { "name": "pm2_5",     "type": "float64",   "role": "measure", "unit": "ug/m3",
      "valid_range": [0, 500], "missing_sentinels": [-999] },
    { "name": "no2",       "type": "float64",   "role": "measure", "unit": "ug/m3",
      "valid_range": [0, 1000] }
  ]
}
```

**Response** `201 Created`:
```json
{ "entity": "sofia_air_quality", "version": 1, "is_latest": true }
```

Roles are graflex's five semantic roles (`entity_id`, `event_time`, `spatial`, `measure`,
`metadata`); the platform maps its upload roles onto these (see §5 step 4). `valid_range`,
`missing_sentinels`, and `domain` are the optional data-quality hints from §5 step 3.

### 8.2 `POST /entities/{entity}/import` — import a CSV against the schema

**Request** — `multipart/form-data`: the raw CSV file, plus the entity it targets. (graflex's
`import_csv_text` takes the CSV body and the entity name.)

**Response** `200 OK` — an **import report** (graflex's `ImportReport`):
```json
{
  "source": "sofia-air-quality.csv",
  "refused": false,
  "missing_required_columns": [],
  "rows_read": 8760,
  "rows_written": 8742,
  "rows_inserted": 8742,
  "rows_updated": 0,
  "row_errors": [
    { "row": 412, "column": "timestamp", "message": "could not parse '2026-13-01' as timestamp" }
  ],
  "violations": [
    { "column": "pm2_5", "null_count": 30, "sentinel_count": 18, "out_of_range_count": 2,
      "unknown_domain_count": 0 }
  ]
}
```

**Refusal** (a required column is missing — nothing is written) is still `200 OK` with:
```json
{ "source": "sofia-air-quality.csv", "refused": true,
  "missing_required_columns": ["timestamp"], "rows_read": 0, "rows_written": 0 }
```

> This is why upload step 5 is **two** calls: schema-save can succeed while import is refused. The
> platform surfaces `refused` / `missing_required_columns` / `row_errors` directly to the user.

### 8.3 `GET /cleaning-steps` — enumerate available cleaning steps

The one genuinely **new** thing graflex must expose (the rest wrap existing functions). Lets the
cleaning dialog build itself from what graflex supports.

**Response** `200 OK`:
```json
{
  "steps": [
    { "type": "replace_sentinels_with_null", "description": "Turn declared missing sentinels into nulls.",
      "params": [] },
    { "type": "fill_missing", "description": "Fill a column's nulls.",
      "params": [
        { "name": "column", "type": "string", "required": true },
        { "name": "method", "type": "enum", "options": ["median", "forward", "constant"], "required": true },
        { "name": "value",  "type": "number", "required": false }
      ] },
    { "type": "enforce_valid_range", "description": "Resolve values outside a column's valid_range.",
      "params": [ { "name": "action", "type": "enum", "options": ["clip", "null", "drop"], "required": true } ] }
  ]
}
```

### 8.4 `POST /entities/{entity}/clean/preview` — preview without writing

**Request** — a cleaning recipe (graflex's `CleaningConfig`: an ordered list of steps):
```json
{
  "steps": [
    { "type": "replace_sentinels_with_null" },
    { "type": "fill_missing", "config": { "column": "pm2_5", "method": "median" } },
    { "type": "enforce_valid_range", "config": { "action": "clip" } }
  ]
}
```

**Response** `200 OK` — per-step before/after (from graflex's `PipelineRun`), nothing written:
```json
{
  "steps": [
    { "type": "replace_sentinels_with_null", "rows_in": 8742, "rows_out": 8742, "cells_changed": 18 },
    { "type": "fill_missing",                "rows_in": 8742, "rows_out": 8742, "cells_changed": 48 },
    { "type": "enforce_valid_range",         "rows_in": 8742, "rows_out": 8742, "cells_changed": 2 }
  ]
}
```

### 8.5 `POST /entities/{entity}/clean` — commit a cleaning recipe

**Request** — same recipe as preview, plus a `name` to save it under:
```json
{
  "name": "sofia_aq/basic",
  "steps": [
    { "type": "replace_sentinels_with_null" },
    { "type": "fill_missing", "config": { "column": "pm2_5", "method": "median" } }
  ]
}
```

**Response** `200 OK` — the cleaning ran, `<entity>__cleaned` was written, the recipe saved:
```json
{
  "entity": "sofia_air_quality",
  "config_name": "sofia_aq/basic",
  "config_version": 1,
  "target_version": "cleaned",
  "rows_written": 8742
}
```

### 8.6 / 8.7 — list and read stored versions

`GET /entities/{entity}/versions` →
```json
{ "versions": [ { "version": "raw", "rows": 8742 }, { "version": "cleaned", "rows": 8742 } ] }
```

`GET /entities/{entity}/versions/{version}?limit=100` → a page of rows (JSON records) for display.

### 8.8 `POST /entities/{entity}/eda` *(optional)* — data-quality report

**Request** — a list of analysis steps (graflex's `EdaConfig`):
```json
{ "steps": [ { "type": "overview" }, { "type": "schema_violations" }, { "type": "missingness" } ] }
```

**Response** — a read-only report (never persisted) the quality view renders. Shape mirrors
graflex's `EdaReport` sections.

### Cross-cutting notes for the contract

- **`project_id`**: graflex scopes everything by a project (default `"default"`). For the MVP the
  platform can pass the implicit default; multi-project is out of scope.
- **Errors**: `400` for a bad schema / bad step config (graflex validates before touching data);
  `404` for an unknown entity/version. An import *refusal* is **not** an error — it is a `200`
  with `refused: true`, because it is an expected, user-facing outcome.
- **Transport**: same posture as the existing Python service — plain HTTP/1.1, multipart only where
  a file is uploaded (endpoint 2); everything else is JSON.

---

## 10. Open questions to resolve together

1. **Which graflex ingestion path does the upload feed?** graflex has both a *generic* per-schema
   import (flexible column shapes) and a *fixed* "collection" table set (hardcoded columns for the
   Sofia air-quality dataset). This plan assumes the **generic** path. We should confirm which one
   the upload should target — it changes step 4/5.
2. **Who authors the schema — interactively at upload, or from a stored template?** This plan
   proposes interactive authoring at upload time.
3. **How much of graflex's later pipeline** (features, splitting, topology, training) do we expose
   to the user for the thesis MVP, versus running with sensible defaults behind the scenes?
4. **Endpoint ownership & timeline** — agreeing the §9 list and who builds what, when.
5. **What id does a prediction return?** graflex persists *training* runs today; does a *prediction*
   call also get a stable, addressable `run_id` (per-prediction), or does it only expose the
   underlying trained-model/run id? This determines what the platform's `graflex_run_id` references
   (see §7). If prediction is stateless, the platform's cached result is the only stored copy of that
   specific prediction — acceptable, but should be a conscious decision.

---

## 11. Summary

- The platform owns the **upload/mapping/cleaning UX** and **authors graflex schemas**; graflex owns
  **storage, processing, and the prediction models**; they talk **over HTTP only**.
- Upload = read headers in the browser → map roles (+ optional quality hints) → build **two** schemas
  → **save schema**, then **import** (two calls, with a clear import report).
- Cleaning = a dedicated tab → pick steps in a dialog (ideally driven by graflex's step catalog) →
  **preview** → **commit** (which also saves a reusable, versioned recipe), guided by a lightweight
  **data-quality view**.
- graflex **supersedes** `data-analysis-service`, but only **after** its HTTP API exists — the old
  service stays until then.
