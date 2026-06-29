# Data path: API to BigQuery

This document describes how data moves from incoming REST calls to BigQuery in `aap-statistikk`.

## 1) Ingestion API (REST)

Incoming events are received over authenticated REST endpoints in `app/src/main/kotlin/no/nav/aap/statistikk/api/mottaStatistikk.kt`.

Main endpoints:

- `/stoppetBehandling` (`StoppetBehandling`)
- `/oppdatertBehandling` (`StoppetBehandling`)
- `/oppgave` (`OppgaveHendelse`)
- `/postmottak` (`DokumentflytStoppetHendelse`)
- `/tilbakekrevingshendelse` (`TilbakekrevingsbehandlingOppdatertHendelse`)

Each endpoint serializes payload and enqueues a Motor job (`JobbAppender`), then returns `202 Accepted`.

## 2) Async processing via Motor jobs

The app wires jobs in `App.kt` and runs them through Motor.

Important jobs in this path:

- `LagreStoppetHendelseJobb`
- `LagreAvklaringsbehovHendelseJobb`
- `LagreSakinfoTilBigQueryJobb`
- `LagreAvsluttetBehandlingTilBigQueryJobb`
- `LagreOppgaveHendelseJobb`
- `LagreOppgaveJobb`

## 3) Domain write path to Postgres

`HendelsesService.prosesserNyHendelse()` is the main orchestrator for `StoppetBehandling`:

1. Finds/creates person and sak
2. Finds/creates/updates behandling + behandling_historikk
3. Stores meldekort when present
4. If status is `AVSLUTTET`, stores extended avsluttet-behandling data
5. Publishes follow-up internal events that enqueue BigQuery-related jobs

Core source tables (Postgres in this repo, schema `public`) are then replicated to BigQuery dataset `datastream_hendelser` by Datastream.

## 4) Two BigQuery delivery paths

### A. Saksstatistikk path (table -> view in BigQuery)

1. `StatistikkHendelse.SakstatistikkSkalLagres` is published
2. `LagreSakinfoTilBigQueryJobb` runs `SaksStatistikkService`
3. Service writes rows to Postgres table `saksstatistikk`
4. Datastream replicates `public_saksstatistikk` to `datastream_hendelser.public_saksstatistikk`
5. BigQuery view `saksstatistikk.view_saksstatistikk` exposes the dataset

### B. Ytelsesstatistikk path (views over replicated source tables)

For avsluttede behandlinger, domain tables are persisted in Postgres (for example `behandling_historikk`, `diagnose`, `diagnose_periode`, `rettighetstype`, `tilkjent_ytelse*`, `grunnlag*`, `samordning_*`), then replicated by Datastream to `datastream_hendelser`.

BigQuery views in `ytelsestatistikk` read those replicated tables, including:

- `view_behandlinger`
- `view_utbetalinger`
- `view_tilkjent_ytelse`
- `view_vilkarsresultat`
- `view_beregningsgrunnlag`
- `view_samordning`

These views now use persisted `oppdatert_tid` fields from source tables for `endret_tid` / `rad_endret_tid`.

### Manual source-table corrections

Manual corrections in Postgres must update `oppdatert_tid` explicitly in the same transaction as the data change. Some child tables do not have their own `oppdatert_tid`; update the parent or another retained row used by the relevant view instead.

Use one timestamp for the whole correction, so rows changed together get the same `oppdatert_tid`:

```sql
BEGIN;

WITH oppdatering AS (
  SELECT CURRENT_TIMESTAMP(6)::timestamp AS oppdatert_tid
),
oppdatert_rettighetstype AS (
  UPDATE rettighetstype r
  SET oppdatert_tid = oppdatering.oppdatert_tid
  FROM oppdatering
  WHERE r.id = (
    SELECT rp.rettighetstype_id
    FROM rettighetstypeperioder rp
    WHERE rp.id = 123
  )
  RETURNING r.id
)
UPDATE rettighetstypeperioder rp
SET
  til_dato = DATE '2026-01-31'
FROM oppdatert_rettighetstype
WHERE rp.id = 123
  AND rp.rettighetstype_id = oppdatert_rettighetstype.id;

COMMIT;
```

If multiple source tables are corrected together, set `oppdatert_tid` on every changed parent or retained row that is read directly or indirectly by a BigQuery view.

### Manual deletes

BigQuery views only read the current replicated rows. If a joined source row is deleted, the view cannot see the deleted row's old `oppdatert_tid`; `endret_tid` / `rad_endret_tid` may therefore stay unchanged or move back to the next-highest timestamp from remaining rows.

When deleting manually, first update a retained row that is included in the relevant view's `endret_tid` calculation, then delete the rows in the same transaction. For behandling-related data, the safest retained row is normally the current `behandling_historikk` row for the behandling.

```sql
BEGIN;

WITH oppdatering AS (
  SELECT CURRENT_TIMESTAMP(6)::timestamp AS oppdatert_tid
),
berort_behandling AS (
  SELECT r.behandling_id
  FROM rettighetstype r
  WHERE r.id = 456
)
UPDATE behandling_historikk bh
SET oppdatert_tid = oppdatering.oppdatert_tid
FROM oppdatering, berort_behandling
WHERE bh.behandling_id = berort_behandling.behandling_id
  AND bh.gjeldende = TRUE;

DELETE FROM rettighetstypeperioder
WHERE rettighetstype_id = 456;

DELETE FROM rettighetstype
WHERE id = 456;

COMMIT;
```

For child-row deletes where the parent row remains, updating the parent row can be enough. For example, before deleting from `rettighetstypeperioder`, update the corresponding `rettighetstype.oppdatert_tid`. If the parent row is also deleted, update a higher-level retained row such as current `behandling_historikk`.

## 5) Existing direct-to-BigQuery write path (still present)

The codebase still contains a direct write path through `BQYtelseRepository` (`behandlinger` table in BigQuery), triggered by `LagreAvsluttetBehandlingTilBigQueryJobb`.

That path is separate from the `datastream_hendelser`-based views above.

## 6) Deployment of BigQuery views

View resources under `.nais/bigquery/*.yml` are deployed by `.github/workflows/deploy_bigquery.yml`.

The workflow deploy order matters when one view depends on another.

## 7) End-to-end sketch

```text
REST API (mottaStatistikk.kt)
  -> Motor queue (JobbAppender)
  -> Job executors
  -> Postgres public tables (source of truth)
  -> Datastream replication
  -> BigQuery dataset datastream_hendelser
  -> BigQuery views (ytelsestatistikk / saksstatistikk)
  -> Downstream consumers
```
