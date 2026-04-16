# TODO: Oppdater view_tilkjent_ytelse med nye graderingsfelter

## Bakgrunn

Nye kolonner ble lagt til i `tilkjent_ytelse_periode` (migrasjon V1.88):

- `samordning_gradering`
- `institusjon_gradering`
- `arbeid_gradering`
- `samordning_uforegradering`
- `samordning_arbeidsgiver_gradering`
- `meldeplikt_gradering`

I tillegg ble `barnepensjon_dagsats` gjort nullable (V1.89).

Disse eksponeres ikke ennå i BigQuery-viewet `view_tilkjent_ytelse.yml`.

## Hva som må gjøres

Legg til følgende kolonner i SELECT-setningen i `.nais/bigquery/view_tilkjent_ytelse.yml`:

```sql
tp.barnepensjon_dagsats,
tp.samordning_gradering,
tp.institusjon_gradering,
tp.arbeid_gradering,
tp.samordning_uforegradering,
tp.samordning_arbeidsgiver_gradering,
tp.meldeplikt_gradering,
```

## Merk

- Alle disse kolonnene er `NULL` for historiske rader (skrevet før V1.88/V1.89).
- Nye rader vil alltid ha reelle verdier.
- Vurder å legge til en `schema_version`-kolonne — se [`bigquery-versioning.md`](bigquery-versioning.md).
