## AAP Statistikk

[![release](https://github.com/navikt/aap-statistikk/actions/workflows/release.yaml/badge.svg)](https://github.com/navikt/aap-statistikk/actions/workflows/release.yaml)

Formålet er å levere statistikk og produkssjonstyringsdata relatert til AAP.

## Komme i gang

Bruker Gradle wrapper, så bare klon og kjør `./gradlew build`.

Dokumentasjon på [sysdok](https://aap-sysdoc.ansatt.nav.no/funksjonalitet/Statistikk/teknisk).

## Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

# For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen `#ytelse-aap-værsågod`.


## Oppdatere Gradle wrapper

For å oppdatere Gradle wrapper: 

```
./gradlew wrapper --gradle-version=8.10.2
```

Commit genererte endringer og push.

## Oppdatere testdata

Når datamodellen endres (f.eks. nye obligatoriske felter legges til), kan testdataene bli utdaterte. Bruk `UpdateHendelserPublicJobbJson.kt` for å automatisk oppdatere testdata-filene:

```bash
./gradlew test --tests "no.nav.aap.statistikk.testutils.UpdateHendelserPublicJobbJson.update_hendelser_public_jobb_fixture_with_utbetalingsdato"
```

Dette scriptet oppdaterer følgende testfiler:
- `app/src/test/resources/hendelser_public_jobb.json`
- `app/src/test/resources/hendelser_klage.json`
- Filer under `app/src/test/resources/avklaringsbehovhendelser/`

Scriptet legger til manglende felter som:
- `årsakTilOpprettelse`
- `perioderMedArbeidsopptrapping`
- `sendtTid` (for oppgave-hendelser, konvertert til ISO-format)
- `utbetalingsdato` (beregnet som tilDato + 1 dag)
- `vedtakstidspunkt`

Commit endringene etter kjøring.
