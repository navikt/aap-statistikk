---
name: nytt-statistikkfelt
description: Legg til statistikkdata fra en innkommende kontrakt via lokal domenemodell og Postgres til riktig dataprodukt. Bruk ved nye felter, perioder, enum-verdier eller vurderingsdata fra behandlingsflyt, oppgave eller postmottak.
---

# Nytt statistikkfelt

Reelle eksempler: `a8287e24` (returårsaker), `df5af5c4` (samordning),
`4f7b39ae` (oppgavemarkering) og `381cd9c6` (retting av enum-mapping).

## Avklar hvor feltet hører hjemme

1. Finn kontrakten og mottaket i [mottaStatistikk.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/api/mottaStatistikk.kt#L1).
   Hendelser kommer med REST POST og legges i Motor, ikke Kafka.
2. Følg feltet til tjenesten og den lokale domenemodellen. Behandlingsdata går gjennom
   [BehandlingService.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/hendelser/BehandlingService.kt#L1);
   avsluttede vurderinger går gjennom
   [AvsluttetBehandlingService.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/avsluttetbehandling/AvsluttetBehandlingService.kt#L1).
3. Avklar om feltet beskriver behandlingen, en historisk tilstand eller periodiserte vurderinger.
   Finn også riktig mottaker: Postgres-data via Datastream og views, saksstatistikk eller direkte BigQuery-skriving.
   Ikke utvid alle dataproduktene automatisk.

## Gjør endringen

1. Skriv først en test for den nye mappingen eller lagringen. Bruk eksisterende testbyggere og fakes.
   Ved ren enum-utvidelse med identiske navn trenger du ikke en egen test.
2. Map kontrakten til lokale typer før domenelogikk og lagring. Se
   [BehandlingsflytKontraktTilDomene.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/hendelser/BehandlingsflytKontraktTilDomene.kt#L1),
   [avsluttetBehandlingTilDomene.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/hendelser/avsluttetBehandlingTilDomene.kt#L1)
   og `tilDomene()` i mottaket. Legg nye kontraktverdier i lokale enums med samme navn;
   ikke map dem til en lignende, men faglig forskjellig verdi.
3. Trengs en kontraktoppgradering, finn versjonskilden i
   [libs.versions.toml](../../../gradle/libs.versions.toml#L1) og
   [settings.gradle.kts](../../../settings.gradle.kts#L1). Ikke legg en ekstra versjon direkte i appens avhengigheter.
4. Ved nytt lagret felt: legg til en ny, ubrukt `V1.<n>__beskrivelse.sql` i `app/src/main/resources/flyway/`.
   Ikke endre kjørte migrasjoner. Sett tidsstempler eksplisitt i repository-koden, aldri med `DEFAULT NOW()`.
5. Oppdater skriving, lesing og eventuell kopiering eller historikkrekonstruksjon.
   For historikkfelt må også [ReberegnHistorikk.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/hendelser/ReberegnHistorikk.kt#L1)
   håndtere feltet. For ny repository: registrer fabrikken i
   [postgresRepositoryRegistry.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/postgresRepositoryRegistry.kt#L1),
   og oppdater relevante [fakes](../../../app/src/test/kotlin/no/nav/aap/statistikk/testutils/fakes/FakeRepositories.kt#L1).
6. Bruk [SamordningRepositoryImpl.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/avsluttetbehandling/SamordningRepositoryImpl.kt#L1)
   som eksempel for perioder. Test lagre/hent og ny lagring med endrede eller fjernede perioder,
   slik at gamle rader ikke blir hengende igjen.
7. Oppdater bare nødvendig utgående mapping. Direkte ytelsesdata har eget skjema og radmapping i
   [BehandlingTabell.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/behandling/BehandlingTabell.kt#L1).
   Saksstatistikk bruker `BQBehandlingMapper` og `BQBehandling`; views følger skill-en `bigquery-view`.
8. Ved endret payload-format: bruk `oppdater-hendelsesfixtures`. Kjør relevante tester underveis og
   `verifiser` til slutt. Ikke oppdater README for et nytt endpoint uten at brukeren ber om det.
