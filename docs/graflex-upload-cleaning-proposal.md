# Proposal: Integrating GraFlex for Data Upload & Cleaning

**Status:** Proposal for discussion
**Date:** 2026-09-26
**Audience:** Thesis supervisor, Lyudmil (GraFlex author)
**Author:** Radoslav

---

## 1. Purpose

This document proposes how the **orchestration platform** (Spring backend + React frontend)
and **GraFlex** (the standalone data platform) should work together for the first two stages
of the pipeline the user sees: **uploading a dataset** and **cleaning it**.

It is meant as a starting point for discussion. It states not just *what* to build, but
*why* each choice was made, so we can disagree on specifics without re-deriving the reasoning.

---

## 2. Background: what each system is

**The orchestration platform** is deliberately *not* an analysis tool. It is a
coordination layer: it ingests datasets, lets a user run **prediction models** against them, and
surfaces the results. It performs no data analysis or modelling itself — it drives the system
(GraFlex) that does.

**GraFlex** is a complete, self-contained data-and-modelling platform. It has its own PostgreSQL database and runs a full pipeline over tabular sensor data:

> schema definition → CSV import → EDA → cleaning → feature engineering → splitting → topology → **model training + prediction**

**The prediction models live inside GraFlex.** GraFlex both prepares the data *and* trains and runs
the models that produce the three prediction kinds the thesis targets — prediction in **time**
(future values at an existing station), in **space** (values at a new location), and in **space +
time**. This makes GraFlex the analytical core, not merely a data-prep service.

GraFlex already runs **standalone** (driven from a notebook today, against its own database),
which matches the requirement that the Python data component work independently of
the Spring/React apps.

**Key consequence:** GraFlex is far more than the current `data-analysis-service` (a small Pandas
stub that only inspects CSV structure and extracts stations). The plan below assumes GraFlex will
**supersede** `data-analysis-service` — see §8.

---

## 3. The core architectural principle

> **The platform authors the schema and owns the user experience.
> GraFlex owns data storage, data processing, and the prediction models.
> The two communicate over HTTP only — the platform never touches GraFlex's database directly.**

Everything below follows from this principle. The reason for the HTTP-only boundary is explained
in §7.

---

## 4. Architecture at a glance

![Component architecture diagram](./architecture.png)

### Components and why each exists

| Component | Why it is there                                                                                                                                                                                                                                                                                                                                                                                                                                        |
|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **React Frontend** | The only thing the user touches. Owns the upload wizard, the column-role mapping UI, and the cleaning dialog. Holds *no* analytical or storage logic — it just calls the backend.                                                                                                                                                                                                                                                                      |
| **Spring Boot Backend** | The **orchestration layer**. It coordinates everything but analyses nothing itself: it authors GraFlex schemas from the user's column mapping, drives GraFlex over HTTP (including asking it to run a prediction), and records what happened.                                                                                                                                                                                                          |
| **Platform DB** (PostgreSQL) | The backend's own store: users (later), registered components, execution records, and **dataset metadata** — including a pointer to the corresponding GraFlex entity + version. It does **not** hold the dataset rows themselves.                                                                                                                                                                                                                      |
| **GraFlex** | The **data platform *and* the model host** — does the actual data work (import, cleaning, features) **and trains + runs the prediction models** (the graph and baseline forecasters that produce the temporal / spatial / spatio-temporal predictions). It is a separate, self-contained system that already runs standalone against its own database, satisfying the requirement that the data component work independently of the Spring/React apps. |
| **GraFlex DB** (PostgreSQL) | GraFlex's own store: entity schemas, saved configs (cleaning recipes, etc.), the actual dataset rows in per-entity `raw` / `cleaned` / `features` tables, and the trained-model / run records. Only GraFlex connects to it.                                                                                                                                                                                                                            | |

---

## 5. Proposed flow — Data Upload

The user uploads one or more station CSV files and tells the platform what each column means; the
platform turns that into a GraFlex **entity schema** and imports the rows into GraFlex.

### Step 1 — Upload CSV file(s) in the UI
The user selects one or more CSV files. (Already implemented.)

### Step 2 — The browser reads the headers and pre-guesses each column's role
This happens **entirely in the browser** — no server call. The frontend already reads the CSV
header line and guesses a role for each column (e.g. a column named `lat` is guessed as latitude).

> **Why client-side, not via GraFlex?** This already works today and needs no network round-trip.
> GraFlex's job begins when we save a schema, not when we read a header line. Sending files to
> GraFlex just to list their columns would add complexity for no benefit.

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
> treat `-999` as a real reading and corrupt every average. GraFlex's cleaning can convert declared
> sentinels to true nulls — but only if we tell it which values are sentinels.

### Step 4 — The platform builds **two** GraFlex schemas
A single upload becomes **two** GraFlex entity schemas, because GraFlex (sensibly) separates a
station's *fixed location* from its *time-varying readings*:

1. a **static station schema** — the station id, its latitude/longitude, and any attributes;
2. a **timeseries measurement schema** — the station id, the timestamp, and the measured values,
   linked back to the station schema by the station id (a foreign-key reference).

> **Why two schemas?** This mirrors how GraFlex models the world (a static "station" entity and a
> timeseries "measurements" entity, joined by the station id) and how our own platform already
> stores data (stations separate from measurements). Splitting them keeps each row lean —
> coordinates are not repeated on every reading — and matches GraFlex's example schemas exactly.

### Step 5 — One **atomic** call to GraFlex: create the dataset (schema + import together)
The platform sends the schema(s) **and** the CSV to a **single** GraFlex endpoint, which saves the
schema(s) and imports the rows **inside one database transaction**: either both land, or neither
does (with an error report). No half-created dataset is possible.

> **Success policy (to confirm with Lyudmil).** Import has two failure shades:
> - **Structural refusal** — the file does not match the schema (a required column is missing).
>   Nothing usable landed → **roll back the whole transaction** (schema included) and return the
>   refusal report.
> - **Row-level coercion errors** — most rows are fine, a few cells fail to parse to their declared
>   type. Recommended: **commit the good rows, report the bad ones** (do *not* roll back) — the very
>   next stage is *cleaning*, whose job is to resolve bad data; a few uncoercible rows should not
>   reject a whole upload. See §10 for this open question.
>
> Either way the platform shows the **import report** (rows read/written, violations, refusal
> reason) in the UI, and only records its own dataset metadata when the call succeeds.
>
> After a successful import the UI also shows **a few sample rows** (≈10, via the read-rows endpoint,
> §9) so the user can
> confirm at a glance that the column mapping landed correctly — the one check the aggregate import
> report and EDA cannot make (a wrong-column mapping can still produce plausible statistics).

### Viewing a dataset — the station map
Opening a dataset shows a **map of its stations**: one marker per station at its lat/lon, click a
marker → that station's metadata (id, name, attributes). This preserves the current platform
behaviour and is the natural spatial view of a station dataset.

The platform reads the **static station entity** in full (via the read-rows endpoint, §9 — a small,
bounded set: tens to
hundreds of stations) and builds the map's GeoJSON `FeatureCollection` from those rows; GraFlex just
returns the station table. Stations without coordinates are simply not plotted. (The station map
answers *"where are the stations, and what are they?"*; the EDA view answers *"how good is the
data?"* — the two are complementary, not alternatives.)

---

## 6. Proposed flow — Data Cleaning

A dedicated **Cleaning** area lets the user turn raw imported data into a cleaned version. The
cleaning *action* is three steps (below); alongside it runs a **data-quality view** that the user
consults *both before and after* — described after the steps, because it is not a step in the
sequence but a view referenced around them.

### Step 1 — A Cleaning tab/section
Operates on the dataset's *raw* version and produces a *cleaned* version. GraFlex keeps these as
separate stored versions (raw, cleaned, features) — cleaning never destroys the original.

### Step 2 — The user picks cleaning steps and their settings in a dialog
GraFlex offers a set of cleaning steps — e.g. *replace missing-value sentinels with null*, *fill
missing values* (median / forward-fill / constant), *enforce valid ranges* (clip / null / drop),
*drop duplicate rows*, *handle outliers*, and more. Each step has its own settings.

> **Recommendation:** GraFlex should expose a "list of available cleaning steps and their settings"
> so the dialog can be **built dynamically** from what GraFlex actually supports, rather than the
> frontend hardcoding each step's form.

### Step 3 — Commit: GraFlex writes the cleaned version; the cleaning recipe is saved
On commit, GraFlex applies the steps, writes the cleaned version, and **saves the cleaning recipe**
(the ordered list of steps) as a reusable, versioned config.

### The data-quality view — consulted *before* and *after* (not a step)
A small data-quality summary — *how many values hit a missing-sentinel, how many are out of range,
time gaps, stuck sensors* — computed by GraFlex's read-only "exploratory analysis" (EDA) stage. It
is the same report run against two versions:

- **Before** (against `raw`) — so the user can *see the problems* and choose the right steps in
  Step 2.
- **After** (against `cleaned`) — so the user can *confirm the steps worked*.

Because it reads whichever version it is pointed at, it is not a stage in the pipeline — it is a lens
the user looks through on either side of the cleaning action.
---

## 7. Why the platform talks to GraFlex over HTTP, never its database directly

Both the platform and GraFlex use PostgreSQL. It is tempting to let the platform read GraFlex's
tables directly. We propose **not** to, and instead go through GraFlex's HTTP API.

**Reasons:**
- **GraFlex's data tables are created dynamically**, named after each entity/version (e.g.
  `station_measurements__cleaned`). If the platform queried them directly, it would be tied to
  GraFlex's internal naming — and any refactoring to that layout would silently break
  the platform.
- **Clean ownership.** GraFlex owns its schema and storage; the platform owns its own (users,
  components, executions, dataset metadata). An HTTP boundary keeps these from entangling.
- **It matches how the platform already works** — the backend already calls the existing Python
  service over HTTP, and the "backend never does data processing itself" rule is already in place.

**Resulting two-database picture:**

| Database | Owner | Who connects |
|---|---|---|
| Platform DB | Spring / Hibernate | **Platform only** — users, components, executions, dataset *metadata* |
| GraFlex DB | GraFlex | **GraFlex only** — schemas, configs, raw/cleaned/feature tables |

The platform reaches dataset *data* by asking GraFlex over HTTP, not by opening a SQL connection to
GraFlex's database.

> **Out of scope here:** a related consequence of this boundary — *where a prediction's result is
> stored* (platform vs. GraFlex, and whether the platform keeps a copy) — concerns the prediction /
> execution flow, not upload & cleaning. It is captured separately in
> [`notes-execution-result-storage.md`](./notes-execution-result-storage.md).

---

## 8. Relationship to the current `data-analysis-service`

The plan assumes GraFlex **supersedes** the current `data-analysis-service`. Recommended sequencing:

1. GraFlex grows the HTTP API this plan needs (see §9). `data-analysis-service` keeps working
   throughout.
2. The new upload + clean flow is pointed at GraFlex, while the old path still functions.
3. Once GraFlex fully covers ingestion and cleaning, `data-analysis-service` is removed.

> **Why not replace it immediately?** GraFlex has **no HTTP API yet** (it is notebook-driven
> today). Removing the working service before its replacement exists would leave the platform
> non-functional. "Supersede then delete" keeps the platform working at every step.

---

## 9. What this requires from GraFlex (the HTTP API to build)

GraFlex currently has no HTTP layer. This plan needs the following endpoints, each a thin wrapper
over a GraFlex function that **already exists** (named in the "GraFlex function" column). This
section is the **contract** between the two workstreams.

| # | Endpoint | GraFlex function | Used by |
|---|---|---|---|
| 1 | `POST /datasets` *(atomic: schema(s) + import in one transaction)* | *(new — wraps `save_entity_schema` + `import_csv_text` in one DB transaction)* | **Upload** |
| 2 | `GET /cleaning-steps` | *(new — enumerate registered steps + param schema)* | Cleaning step 2 |
| 3 | `POST /entities/{entity}/clean` | `save_cleaning_config` + `clean` | Cleaning step 3 |
| 4 | `POST /entities/{entity}/eda` | `run_eda` | Data-quality view, before/after cleaning (§6) |
| 5 | `GET /entities/{entity}/versions` | `list_versions` | Which versions exist (drives the UI) |
| 6 | `GET /entities/{entity}/versions/{version}` | `read_version` | Station rows (map) + measurement sample (mapping check) |

> **Ingestion is deliberately a single endpoint.** There is no standalone "save a schema" or
> "import into an existing entity" in the contract: the MVP treats one upload as one dataset, so the
> atomic `POST /datasets` is the only way data enters. This keeps the API minimal and makes an
> orphaned schema *structurally impossible* — there is no way to create a schema without data. (The
> underlying `save_entity_schema` / `import_csv_text` functions still exist inside GraFlex; they are
> just not exposed separately. Appending data to an existing dataset is a deferred question — see §10.)
---

## 10. Open questions to resolve together

1. **Confirm the ingestion target.** The plan assumes GraFlex's **generic per-schema import**
   (the user authors a schema at upload; data lands in `<entity>__raw`). GraFlex also has a *fixed*
   "collection" table set (hardcoded columns for the Sofia air-quality dataset), written by a separate
   acquisition component. Confirm that the generic path is the one the upload should feed.
2. **How much of GraFlex's later pipeline** (features, splitting, topology, training) do we expose
   to the user?
3. **Partial-import success policy.** The atomic `POST /datasets` (§9) rolls back on a *structural
   refusal* (a required column is missing). But when *some rows* fail type coercion while most are
   fine, do we **commit the good rows and report the bad ones** (recommended — cleaning handles the
   rest), or **roll back the whole upload** on any row error, or apply a **threshold** (commit if the
   error rate is below X%)?
4. **Can a dataset accumulate data from multiple uploads?** The MVP treats one upload as one dataset,
   so the contract exposes only the atomic `POST /datasets`. If a dataset should instead grow over
   time (e.g. upload January, then February, into the same entity), a standalone "import into an
   existing entity" endpoint is needed — deferred, and purely additive if it turns out to be wanted.
