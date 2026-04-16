# BigQuery Schema Versioning — Suggestion

## Problem

When the DB schema evolves (new columns added), rows written before the change will have `NULL` in
those columns. BigQuery consumers cannot currently distinguish:

- "This field is genuinely absent for this record"
- "This record predates the feature and was never populated"

BigQuery's built-in time travel (max 7 days) is too short for analytical use.

## Researched Options

| Option                                      | Pros                                          | Cons                                                 |
| ------------------------------------------- | --------------------------------------------- | ---------------------------------------------------- |
| **BigQuery time travel**                    | Built-in, zero work                           | Max 7 days; not useful for long-term analytics       |
| **BigQuery table snapshots**                | Point-in-time backups                         | Manual/scheduled, not queryable as history           |
| **SCD Type 2 via scheduled query**          | Full row history with `valid_from`/`valid_to` | Requires new pipeline/infra, adds complexity         |
| **`schema_version` int column in Postgres** | Explicit, propagates via Datastream to BQ     | Extra column to maintain; needs migration per change |
| **Derived `schema_version` in the BQ view** | Zero infra, immediately useful                | View-only; consumers can filter but not partition on |

## Recommended Approach: Derived column in the view

Add a `schema_version` integer to `view_tilkjent_ytelse`:

```sql
CASE
  WHEN tp.samordning_gradering IS NULL THEN 1
  ELSE 2
END AS schema_version
```

- **Version 1** — rows written before samordning/gradering fields were collected
- **Version 2** — rows with full gradering data

### Why this approach

- Zero infrastructure cost
- Immediately useful: consumers can `WHERE schema_version = 2` to restrict to complete rows
- Self-documenting: the logic lives in the view definition
- Easy to evolve: bump the version and add a new `WHEN` clause when the next field is added

### Future upgrade path

If indexing or partitioning on `schema_version` becomes necessary, promote it to a real integer
column in the Postgres table (with a Flyway migration), which will propagate naturally via
Datastream to BigQuery.
