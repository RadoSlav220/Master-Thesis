# Notes: Where execution / prediction results are stored (platform vs. GraFlex)

**Status:** Notes for later — **out of scope** for the upload & cleaning proposal, kept here so the
reasoning is not lost. This concerns the *prediction / execution* flow, not upload or cleaning.
**Date:** 2026-09-27

> Extracted from the "GraFlex upload & cleaning" proposal, where it had drifted in via the
> database-boundary discussion. It belongs to a future proposal about **running predictions**.

---

## Context

The platform and GraFlex each have their own PostgreSQL database, and the platform reaches GraFlex
**only over HTTP** (never by direct SQL) — to avoid coupling to GraFlex's dynamically-named tables.
A consequence of that boundary is the question below: when a prediction runs, **where does its result
live?**

## The two things a prediction produces

Running a prediction produces two related-but-different things, with different natural owners:

1. **The execution record** — *"user X ran model Y against dataset Z at time T; status; the
   measurement mapping used."* This is **orchestration provenance** and lives in the **Platform DB**.
   GraFlex structurally cannot hold it: GraFlex has no concept of the platform's users, its registered
   components, or its dataset ids — and, crucially, **some executions never reach GraFlex at all** (an
   invalid mapping rejected with `400`, or GraFlex being unreachable). Those still need an execution
   row; only the platform can provide it.
2. **The prediction output + the reproducible run** — the predicted values, plus the config / metrics
   / trained model that produced them. GraFlex **owns this** (it is the system that computed it, and
   its own pipeline needs the run record for reproducibility and re-analysis).

## How they link — reference, don't duplicate

GraFlex keeps a `run_id → result` mapping in its own store. The Platform DB's `executions` table
carries a **`graflex_run_id`** column that references that run. This is a **logical reference resolved
over HTTP** (e.g. `GET /runs/{run_id}`), **not** a SQL foreign key — the two databases cannot join,
and nothing enforces integrity across them.

**`graflex_run_id` is nullable** — populated when GraFlex actually ran, null for executions that
failed before reaching GraFlex. (This is a second reason the execution record must live in the
platform.)

## The open trade-off: does the platform also store a copy of the result?

Two reasonable options:

- **(A) Store a copy** of the result on the execution row (a read-model copy). Displaying a past
  execution then does not depend on GraFlex being up and does not re-query GraFlex on every view.
  This *duplicates only the result payload* (not the model/config/run) — a deliberate denormalization
  that is safe here because a completed prediction is **immutable**, so the copy can never drift from
  the source. Cost: storage, which matters only for large payloads.
- **(B) Reference only** — store just `graflex_run_id` and fetch the result from GraFlex on demand.
  No duplication, but the UI's ability to show results is then coupled to GraFlex's availability, and
  a run GraFlex later drops becomes an unviewable dangling reference.

**Leaning: (A)** — it keeps the read path fast and decoupled (the same reason the platform already
caches fetched-dataset analysis), and the immutability of a completed prediction removes the usual
risk of duplicated data (inconsistency). Either way, if GraFlex produces a new run that is a **new
execution**; an existing execution's result is never mutated in place.

**Not decided — to confirm with Lyudmil.**

### If option (A): how the copy is written

It is not a managed cache — it is **write-once at execution time**, no invalidation:

1. Create the `execution` row: `status = RUNNING`, `graflex_run_id = null`, `result = null`.
2. Call GraFlex; on success GraFlex returns the `run_id` **and** the result payload.
3. In the same handler, write `graflex_run_id`, `result`, `status = COMPLETED`, `finished_at`.
4. On failure (`400` / timeout): `status = FAILED`, `error_message`; `graflex_run_id` and `result`
   stay null.

Because a completed prediction is immutable, the stored copy can never go stale — so there is no TTL,
no invalidation, and no cache-miss/refetch path. Reads are a plain local `SELECT`.

### Large payloads (the escape hatch)

Storing the result inline in the `executions` table is fine for small payloads (a few hundred
predicted points). For **large** results (dense spatial grids, long horizons × many stations), either
put the copy in a separate `execution_results` table (1:1, keeps `executions` lean for listing) or
fall back to reference-only (B) for those. Inline is the MVP default; do not build the indirection
until result size demands it.

## Resulting `executions` shape (platform side)

```
id                (platform UUID — the platform's own execution id)
user_id, component_id, dataset_id, measurement_mapping
status, created_at, finished_at, error_message
graflex_run_id    (nullable — reference to GraFlex's run; null if it never reached GraFlex)
result            (nullable — copy of GraFlex's output for display; only if option (A) is chosen)
```

## Open questions (for the future prediction proposal)

1. **What id does a prediction return?** GraFlex persists *training* runs today; does a *prediction*
   call also get a stable, addressable `run_id` (per-prediction), or does it only expose the
   underlying trained-model/run id? This determines what `graflex_run_id` points at. If prediction is
   stateless, a platform-side copy (option A) would be the only stored copy of that specific
   prediction — acceptable, but a conscious decision.
2. **Store a copy of the result, or reference only?** (Options A/B above.) Leaning A; large payloads
   get the separate-table or reference-only escape hatch. To decide with Lyudmil.
