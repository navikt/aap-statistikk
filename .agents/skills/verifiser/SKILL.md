---
name: verifiser
description: Verifiser endringer i aap-statistikk med relevante tester, full Gradle-testkjøring og nødvendige format- og kvalitetskontroller. Bruk før arbeidet er ferdig eller før en PR. Tar valgfritt en basis-ref.
argument-hint: "[basis-ref, for eksempel origin/main]"
---

# Verifiser endringene

1. Les [AGENTS.md](../../../AGENTS.md#L1) og skill brukerens eksisterende endringer fra dine.
   Finn både branch-diff, arbeidskopi og nye filer. Bruk oppgitt basis-ref, ellers `origin/main` hvis den finnes:

    ```bash
    git diff --name-only <basis-ref>...HEAD
    git diff --name-only HEAD
    git ls-files --others --exclude-standard
    ```

    Ikke stage, unstage eller rydd bort brukerens endringer.

2. Dette repoet har én app-modul, `app`, og egen `build-logic`.
   Finn JDK-kravet i [gradle.properties](../../../gradle.properties#L1).
   Behold daemon og configuration cache.
3. Kjør relevante tester underveis, med flere selektorer i samme kall når de dekker endringen:

    ```bash
    ./gradlew :app:test --tests "*BehandlingRepositoryTest" --tests "*HendelseHjelpereKtTest"
    ```

    Velg tester etter endringen:
    - Mapping eller historikk: `HendelseHjelpereKtTest`, `ResendHendelseServiceTest` og relevant repository-test.
    - Saksbehandler, enhet eller rekkefølge: `SakstatistikkEventSourcingTest`, `BQBehandlingMapperTest`,
      `SaksStatistikkServiceTest` og eventuell jobbtest.
    - REST-mottak eller Motor-kølegging: `MottaStatistikkTest` og `MotorJobbAppenderTest`.
    - Fixtures eller hendelsesforløp på tvers av lag: `IntegrationTest`.
    - Migrasjon: relevant repository-test mot Postgres som kjører migrasjonene.

4. Kjør `./gradlew test` til slutt. Testene kan bruke Testcontainers for Postgres og BigQuery-emulatoren;
   Docker må være tilgjengelig. Ikke anta at en grønn enhetstest verifiserer SQL eller migrasjoner.
   Vanlig `test` skal ikke skrive fixture-filene. Kjør `:app:oppdaterHendelsesfixtures` eksplisitt
   hvis de trenger vedlikehold. Kontroller diffen etterpå, og ikke forkast brukerens endringer.
5. Ved vesentlige Kotlin-endringer: kjør `./gradlew detektMain`. Appen setter `ignoreFailures = true`
   i [build.gradle.kts](../../../app/build.gradle.kts#L11), så les rapporten selv om kommandoen lykkes.
   Rett nye funn som endringen introduserer; ikke utvid oppgaven med urelaterte funn.
6. Kjør `npx prettier --write <konkrete-filer>` for alle Markdown- og JSON-filer du har endret.
   Rene dokumentasjons- og skill-endringer trenger ikke Kotlin-kompilering; kontroller frontmatter,
   filreferanser og kommandoer. Følg fortsatt eventuelle eksplisitte krav om full testkjøring.
   For BigQuery-views: bruk også SQL-kontrollene i `bigquery-view`.
7. Kjør `git diff --check` og kontroller endelig diff, inkludert nye filer. Ikke kall arbeidet verifisert
   hvis kompilering, tester eller nødvendige kontroller er blokkert. Oppgi hva som faktisk ble kjørt,
   hva som feilet og om feilen skyldes miljøet eller kode som er endret.

Ikke commit, push, deploy eller start rekjøring som del av denne skill-en.
