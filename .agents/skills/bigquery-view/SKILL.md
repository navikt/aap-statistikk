---
name: bigquery-view
description: Opprett eller endre BigQuery-views under .nais/bigquery med riktige datakilder, tidsstempler, filtre og deployment-rekkefølge. Bruk ved nye dataprodukter, kolonner, joins eller feil i statistikk-views.
---

# Opprett eller endre et BigQuery-view

Reelle eksempler: `36648f30` (samordning), `2b41250a` (siste gjeldende tilstand),
`3cb18c72` (miljøuavhengige referanser), `38909a20` (null før Datastream-synk).

1. Finn nærmeste view i `.nais/bigquery/`. Avklar datasett og radnivå før du skriver SQL:
   én rad per behandling, historikkhendelse eller periode. Flere periode-joins kan multiplisere rader.
2. Skriv først et lite dataeksempel med forventede rader for den endrede SQL-logikken.
   Dekk tilfellene som faktisk berøres: flere perioder, manglende relasjon, soft delete eller flere historikkrader.
   Bruk eksisterende testoppsett hvis det dekker viewet; ikke opprett et nytt rammeverk bare for denne endringen.
3. Bruk `BigQueryTable` med `view.useLegacySql: false`. Ved nytt view må `metadata.name`
   være unikt, og `spec.resourceID` være SQL-navnet. Kopier metadata fra et eksisterende view.
   `metadata.name`, filnavnet og `resourceID` kan være forskjellige.
4. Behold `{{project}}` for prosjektet i manifestet. Ikke hardkod dev- eller prod-prosjekt i SQL.
   Bruk datasettkvalifiserte referanser som i
   [view_siste_gjeldende_per_behandling.yml](../../../.nais/bigquery/view_siste_gjeldende_per_behandling.yml#L1).
5. Skill mellom Postgres-data som Datastream kopierer til `datastream_hendelser.public_*`,
   saksstatistikk og tabeller som appen skriver direkte til BigQuery. Kontroller kolonnenavn og datatyper
   mot kilden, ikke bare mot et lignende view.
6. Bevar nødvendige filtre for `gjeldende` og `slettet`. Velg `LEFT JOIN` når rader uten
   vurderingsdata også skal være med, for eksempel avslag. Ikke bruk `DISTINCT` til å skjule en feil join.
7. Ved persondata: les skjermings- og identfiltrene i
   [view_ateam_behandlinger.yml](../../../.nais/bigquery/view_ateam_behandlinger.yml#L1).
   Der brukes aktiv ident og `p.skjermet IS NOT TRUE`, fordi skjerming kan være null før synk.
   Dette er et eksisterende, produktspesifikt valg, ikke en generell regel om at ukjent skjerming er trygg.
   Ikke svekk andre produkters tilgang eller skjermingskrav ved å kopiere filteret ukritisk.
8. Bevar betydningen av tidene. `hendelsestidspunkt` er ikke det samme som `oppdatert_tid`
   eller `datastream_metadata.source_timestamp`. Når kilden er lokal `DATETIME`, følg eksplisitt
   `Europe/Oslo`-konvertering der viewet bruker det. Ikke konverter et allerede korrekt `TIMESTAMP` på nytt.
9. Ved nytt view: legg manifestet i `RESOURCE` i
   [deploy_bigquery.yml](../../../.github/workflows/deploy_bigquery.yml#L22).
   Views som andre views bruker, må stå først. Ved endring: sjekk avhengige views og om tilsvarende
   endring også skal gjelde `view_behandlinger_test`, ikke kopier automatisk over produksjonsviewet.
10. Kontroller SQL med det tilgjengelige testoppsettet eller en autorisert BigQuery dry run.
    Kotlin-tester alene beviser ikke at BigQuery godtar SQL-en. Si eksplisitt fra hvis bare manifest,
    datakilder og deployment-rekkefølge er kontrollert. Ikke deploy som del av lokal verifisering.

Avslutt med `verifiser`. Ikke legg til rå personopplysninger som testdata.
