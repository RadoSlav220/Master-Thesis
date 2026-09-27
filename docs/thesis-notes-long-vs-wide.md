# Thesis notes: Long vs. Wide storage format for measurement data

**Purpose:** working notes for the thesis writeup — the reasoning behind storing sensor
measurement data in **wide** format rather than **long/tall** format. Written to be *defensible*:
it states the trade-offs, when each format wins, why the alternative was rejected, and the threats
to the decision. Not a final chapter — raw material for one.

---

## 1. The question

Time-series sensor data (measuring stations reporting quantities like PM2.5, NO₂, O₃, temperature
over time) can be persisted in two canonical shapes. Which should the platform's data store use?

**Long / tall format** — one row per single reading:

| point_id | timestamp           | variable | value |
|----------|---------------------|----------|-------|
| S1       | 2026-01-01T00:00:00 | pm2_5    | 33.0  |
| S1       | 2026-01-01T00:00:00 | no2      | 12.4  |
| S1       | 2026-01-01T01:00:00 | pm2_5    | 35.1  |

**Wide format** — one row per (station, timestamp), each variable its own column:

| point_id | timestamp           | pm2_5 | no2  | o3   |
|----------|---------------------|-------|------|------|
| S1       | 2026-01-01T00:00:00 | 33.0  | 12.4 | 40.2 |
| S1       | 2026-01-01T01:00:00 | 35.1  | 11.9 | 38.7 |

---

## 2. Decision

**Store measurement data wide and typed** (one typed column per measured variable), aligned with the
declared entity schema.

---

## 3. Why — the reasoning, from the data's purpose

The decisive argument is **what the data is ultimately for**. In this system the end goal is
**prediction models** (forecasting in time, in space, and in space + time). A model consumes a
**feature matrix**, which is inherently wide: columns are variables, rows are observations, and a
model's input vector at a point in time is a row across the variable columns. Feature-engineering
operations that precede modelling — lagged values, rolling-window aggregates, normalization,
inter-variable correlations — all address **variables as named columns**.

Long format is therefore not a *modelling* format; it is a *storage/ingestion convenience*. Data
stored long must be **pivoted to wide before it can be modelled**. So the real question is not "long
or wide" in the abstract, but **where the long→wide pivot is paid**:

- **Store long** → pivot on *every* downstream read (every cleaning step, feature build, model run).
- **Store wide** → pivot **once**, at import time; every downstream stage reads the shape it needs.

For a system whose entire purpose is repeatedly feeding models, paying the pivot **once at write
time** dominates paying it **many times at read time**.

---

## 4. Trade-off analysis (both directions honestly)

### Where WIDE wins
- **Model-ready.** The feature matrix *is* wide; no reshape before training.
- **Typed columns.** Each variable is a real typed column (e.g. `float64`), so range checks,
  arithmetic, normalization, and ML operate directly on it — no per-value type ambiguity.
- **"All variables at time T for station S" is one row** — the query modelling and visualization
  most often need.
- **Constraints are natural.** Per-column validity ranges, units, and missing-value sentinels attach
  to a column, which is where cleaning and profiling expect them.

### Where LONG wins
- **Schema flexibility.** A new measured variable is just new rows — no schema change. Good when the
  variable set is unknown upfront or varies per station.
- **Sparsity / irregularity.** Only readings that exist are stored; no wasted cells when a station
  does not report a given variable.
- **Uniform ingestion.** Any variable, any station, one insert path — no need to know the column set
  in advance.

### The cost WIDE imposes (and why it is acceptable here)
Wide's weakness is that **the set of measured variables must be known at import time**, and adding a
variable is a schema change (a new column). This is a genuine narrowing versus a schemaless long
store that discovers variables lazily. It is acceptable in this domain because:
- **Environmental sensor networks have a bounded, stable variable set** (a fixed list of pollutants
  and meteorological quantities). This is not open-ended free-text data.
- The platform's ingestion is **schema-first by design** — the user already declares column roles and
  types at upload. Committing to a variable set is a step that already exists, not a new burden.
- **Sparsity is handled by nulls**, which the cleaning stage explicitly resolves (fill / drop /
  flag). A missing reading becoming a null is a *modelled* concern, not wasted space — and at
  city-sensor scale the storage overhead is negligible.

---

## 5. When the decision would flip

The choice is domain-dependent, not universal. **Long would be the correct choice if:**
- the variable set were **open-ended or unknown** (e.g. arbitrary user-supplied datasets with wildly
  varying columns), or
- the data were **extremely sparse and high-cardinality** (thousands of rarely-reported variables),
  making a wide table mostly nulls.

This platform's domain is **neither**: a bounded set of numeric environmental variables destined for
forecasting models. That is precisely the case where wide is strongest and long's advantages do not
pay off. Stating this boundary explicitly is what makes the decision defensible rather than dogmatic.

---

## 6. Corroborating constraint: the modelling platform's architecture

Independently of the first-principles argument, the analytical platform the data feeds into
(graflex) is **built around wide, typed, one-column-per-variable tables**: its entity schemas declare
each measure as a typed column; its stored versions (raw / cleaned / features) are wide; and its
cleaning steps, feature steps, and models all address **columns by name**. Storing long would force a
pivot in front of every one of those stages. So the wide choice is reinforced from two independent
directions — the abstract "data serves models" argument *and* the concrete architecture of the system
that runs the models.

> Note for the writeup: present the first-principles argument (§3–§5) as the primary justification.
> The platform-architecture point (§6) is corroborating evidence, not the reason — otherwise the
> decision reads as "we did what the tool wanted" rather than "the tool and the reasoning agree."

---

## 7. Consequence: the pivot becomes an explicit ingestion step

Choosing wide places the **long→wide pivot at import time**. In practice the user's per-column role
mapping (assigning columns as station-id / location / timestamp / measurement) is what declares which
columns become the wide typed measurement columns. Sensor CSVs frequently arrive *long* (a `variable`
/ `value` pair) or *semi-wide*; the ingestion layer is where they are reshaped and typed into the
stored wide form. This is a deliberate, one-time cost paid at the boundary, consistent with the
"pivot once, at write time" reasoning in §3.

---

## 8. One-paragraph summary (draft for the thesis)

> Measurement data is stored in **wide** format — one row per (station, timestamp) with each measured
> variable as its own typed column — rather than in long/tall format. The decision follows from the
> data's purpose: it exists to feed forecasting models, whose input is inherently a wide, typed
> feature matrix, so any long representation must be pivoted to wide before use. Storing wide pays
> that pivot **once at ingestion** instead of on every downstream read. Long format's advantages —
> schema flexibility for unknown variable sets and efficient handling of extreme sparsity — do not
> apply to this domain, where the set of environmental variables is bounded and stable and the data
> is destined for modelling. Wide's one cost, committing to a known variable set at import time, is
> already implied by the platform's schema-first ingestion and is therefore not an added burden.
