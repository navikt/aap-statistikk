---
name: oppdater-hendelsesfixtures
description: Oppdater JSON-fixtures for statistikkhendelser ved kontraktendringer eller nye genererte scenarier fra behandlingsflyt. Bruk ved deserialiseringsfeil, manglende payload-felter eller endrede forventninger i IntegrationTest.
---

# Oppdater hendelsesfixtures

Reelt eksempel: `350b4db5` oppdaterer scenarier fra behandlingsflyt og tilhørende tester.
Velg mellom å tilpasse eksisterende payloads og å hente et nytt generert hendelsesforløp.

## Tilpass eksisterende payloads

1. Les deserialiseringsfeilen og den aktuelle kontrakten. Skriv eller finn testen som viser hvilket felt
   som mangler. Ikke legg inn tilfeldige standardverdier for å få parseren til å godta dataene.
2. Bruk [UpdateHendelserPublicJobbJson.kt](../../../app/src/test/kotlin/no/nav/aap/statistikk/testutils/UpdateHendelserPublicJobbJson.kt#L25).
   Hjelperen håndterer både direkte `StoppetBehandling`-objekter og Motor-rader der `payload`
   selv er en JSON-streng. Den skriver til kildefilene og kjører også som del av vanlig `test`.
3. For nye transformasjoner: implementer `NodeTransform` og legg transformasjonen i `behandlingTransforms`.
   Bevar eksisterende verdier, og bruk bare standardverdier som er riktige for testscenarioet.
4. Ta vare på oversikten over eksisterende endringer før kjøring. Kjør fra repo-roten:

    ```bash
    ./gradlew :app:test --tests "no.nav.aap.statistikk.testutils.UpdateHendelserPublicJobbJson.update_hendelser_public_jobb_fixture_with_utbetalingsdato"
    ```

5. Hjelperen behandler en eksplisitt filliste. Legg nye fixture-filer til der hvis de også skal transformeres.
   Kontroller diffen for utilsiktede endringer av historikk, tidspunkt og verdier.

## Hent et nytt hendelsesforløp fra behandlingsflyt

1. Finn generatoren i den aktuelle checkouten av `aap-behandlingsflyt`, og sjekk oppgaver og utdata før kjøring.
   Commit `350b4db5` viser den historiske kommandoen `:behandlingsflyt:genererStatistikkFixtures`;
   ikke anta at oppgaven eller utdataområdet fortsatt finnes.
2. Kopier bare avtalte scenarier til `app/src/test/resources/avklaringsbehovhendelser/`.
   Ikke overskriv brukerens eksisterende fixture-endringer. Bruk syntetiske data.
3. Skill endret hendelsesforløp fra endret forventet statistikk i
   [IntegrationTest.kt](../../../app/src/test/kotlin/no/nav/aap/statistikk/IntegrationTest.kt#L1).
   Forklar faglig hvorfor forventningene endres; ikke oppdater dem bare fordi testen feiler.

## Kontroller resultatet

Kjør `npx prettier --write` med de konkrete JSON-filene som ble endret.
Kontroller også at eventuelle JSON-strenger inne i `payload` lar seg deserialisere.
Kjør berørte tester, for eksempel:

```bash
./gradlew :app:test --tests "*IntegrationTest" --tests "*HendelseHjelpereKtTest" --tests "*ResendHendelseServiceTest"
```

Kjør transformasjonshjelperen på nytt hvis du endret den. Den skal ikke gi nye semantiske endringer.
Formater berørte JSON-filer igjen dersom hjelperen skriver dem på nytt. Avslutt med `verifiser`.
