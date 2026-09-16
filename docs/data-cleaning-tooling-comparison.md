# Data Cleaning Tooling: OpenRefine vs. Config-Driven Python Pipeline

**Context:** The Local Digital Twin platform must perform data cleaning and preprocessing driven by a **user-uploaded configuration file** (no GUI, no human-in-the-loop at runtime). This document compares reusing OpenRefine's ready-made functionality against building a config-driven pipeline in Python (Pandas + PyArrow + scikit-learn).

## Comparison

| Criterion | OpenRefine (server + recipe replay) | Config-driven Python pipeline |
|---|---|---|
| User authors config by hand | ❌ Recipe JSON is GUI-generated, not human-writable | ✅ Clean, readable YAML/JSON schema |
| Headless / no GUI at runtime | ❌ No batch mode; requires running a Jetty server | ✅ Native |
| Fits microservice orchestration | ⚠️ Heavy, stateful JVM service to manage | ✅ Extends existing FastAPI `data-analysis-service` |
| Reproducibility | ✅ Via serialized recipes | ✅ Via versioned config file |
| Standard cleaning (dedup, impute, reshape, cast, outliers) | ✅ Supported | ✅ Pandas / scikit-learn |
| Fuzzy text clustering / Wikidata reconciliation | ✅ Best-in-class | ⚠️ Possible via libraries (`recordlinkage`, `dedupe`, `rapidfuzz`), more effort |
| Deployment weight | ⚠️ Extra JVM container, disk-persisted project state | ✅ Lightweight, stateless |
| Programmatic control | ⚠️ HTTP API against a stateful server only | ✅ Direct in-process calls |

## Key Findings (evidence from OpenRefine source)

- **No headless/batch mode.** `Refine.java`'s `main()` only starts a Jetty HTTP server with the web UI. There is no `refine input.csv recipe.json output.csv` runner.
- **Server-centric, stateful.** Cleaning operations couple to a running `Project` + `ProcessManager`; projects are persisted to disk. It is a standalone application, not an embeddable library.
- **Config format is GUI-generated.** Cleaning steps are stored as recipe JSON (`Recipe.java`, `OperationRegistry.java`) using internal operation IDs (e.g., `core/column-rename`). This is produced by the GUI, not meant for hand-authoring — conflicting directly with the "user-uploaded config" requirement.
- **Automation is possible but heavy.** Via HTTP: `create-project` → `apply-operations` (recipe JSON) → `export-rows`. Requires running and lifecycle-managing the server.
- **Only GREL expression parsing is reusable standalone.** Full operations are not.

## Decision

Use a **config-driven Python pipeline** (Pandas + PyArrow + scikit-learn) extending the existing `data-analysis-service`.

**Rationale:** OpenRefine excels at interactive, human-driven exploratory cleaning with recipe replay. A Local Digital Twin platform requires automated, config-driven, reproducible batch cleaning — a fundamentally different mode of operation — better served by a declarative pipeline. OpenRefine's only unique edge (interactive fuzzy clustering + entity reconciliation) is unlikely to be needed for numeric sensor time-series data.

**Revisit OpenRefine only if** messy free-text categorical columns requiring fuzzy clustering or external entity reconciliation appear.

## Example config schema (illustrative)

```yaml
# cleaning-config.yaml (user-authored, readable)
steps:
  - op: drop_duplicates
    keys: [stationId, timestamp, measurementType]
    keep: last
  - op: impute
    column: pm25
    method: knn          # scikit-learn KNNImputer
  - op: cast
    column: timestamp
    to: datetime
  - op: filter_outliers
    column: pm25
    method: iqr
  - op: reshape
    from: wide
    to: long
```
