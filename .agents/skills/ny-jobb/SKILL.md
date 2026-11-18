---
name: ny-jobb
description: Legg til eller endre en Motor-jobb i statistikk med JobbSpesifikasjon, registrering, kølegging og tester. Bruk ved asynkron hendelsesbehandling, nye jobbtyper eller endringer i payload og retry.
---

# Ny Motor-jobb

Reelle eksempler: `2346e11b` (tilbakekreving), `c92aced1` (jobb og utfører i samme fil),
`37757e2e` (cutoff gjennom retry).

1. Skriv først en test for jobbens virkning med fakes. Se
   [MotorJobbAppenderTest.kt](../../../app/src/test/kotlin/no/nav/aap/statistikk/jobber/MotorJobbAppenderTest.kt#L1)
   og [MotorTestHelpers.kt](../../../app/src/test/kotlin/no/nav/aap/statistikk/testutils/motor/MotorTestHelpers.kt#L1).
   Test gjentatt utføring der jobben kan leveres på nytt.
2. Legg jobbspesifikasjon og `JobbUtfører` i samme fil nær fagområdet.
   Bruk `JobbInput` og en eksplisitt payload. Jobbens `type` er en stabil streng som lagres i databasen;
   ikke endre eksisterende typer ved en vanlig refaktorering.
3. Velg riktig konstruksjon:
    - `ProviderJobbSpesifikasjon` når bare `RepositoryProvider` trengs.
    - `ProvidersJobbSpesifikasjon` når også `GatewayProvider` trengs, for eksempel ved PDL eller skjerming.
      Se [LagreStoppetHendelseJobb.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/jobber/LagreStoppetHendelseJobb.kt#L22).
      Gjenbruk `lagSaksStatistikkService` når jobben trenger saksstatistikktjenesten.
4. Registrer spesifikasjonen i `jobberListe` i
   [App.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/App.kt#L122).
   Det finnes ikke en `ProsesseringsJobber.alle()` i dette repoet.
5. Legg jobben i kø gjennom [JobbAppender](../../../app/src/main/kotlin/no/nav/aap/statistikk/jobber/appender/JobbAppender.kt#L1)
   og [MotorJobbAppender](../../../app/src/main/kotlin/no/nav/aap/statistikk/jobber/appender/MotorJobbAppender.kt#L19)
   i samme transaksjon som dataendringen. For jobber knyttet til sak: følg eksisterende `forSak(...)`
   på den aktuelle innleggingsveien. Ikke forveksle eksternt saksnummer med intern sak-ID.
6. Skal en hendelse utløse videre arbeid, gjenbruk `HendelsePublisher` og oppdater relevante fakes og tester.
   Nye REST-mottak bruker `mottaHendelse` i
   [mottaStatistikk.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/api/mottaStatistikk.kt#L41)
   med riktig `authorizedAzps`, og registreres i `Application.module`. Ikke legg til Kafka.
7. Payload-endringer må tåle jobber som allerede ligger i kø. Se bakoverkompatibel lesing i
   [LagreSakinfoTilBigQueryJobb.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/LagreSakinfoTilBigQueryJobb.kt#L51).
   Legg til en test med gammel payload når formatet endres.
8. Skill Motor-retry fra domenets planlagte retry. Saksstatistikk planlegger ny jobb ved manglende enhet,
   beholder cutoff og henter data på nytt. Feil etter siste forsøk skal være synlig, ikke bli en vellykket no-op.
   Ikke kopier retry-antall eller backoff fra en annen jobb uten å vurdere behovet.
9. Test kølegging og utføring. Ved nytt REST-mottak: utvid
   [MottaStatistikkTest.kt](../../../app/src/test/kotlin/no/nav/aap/statistikk/api/MottaStatistikkTest.kt#L1)
   med riktig autorisasjon og forventet jobb. Bruk eksisterende manuelle Motor-oppsett.

Avslutt med `verifiser`. Ikke start jobber i dev eller prod uten eksplisitt bestilling.
