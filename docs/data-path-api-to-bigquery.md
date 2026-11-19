# Dataflyt fra API til BigQuery

`aap-statistikk` mottar hendelser via REST, behandler dem i Motor-jobber og
lagrer domenehistorikk og statistikk i Postgres. Datastream replikerer tabeller
til BigQuery. En egen jobb skriver også direkte til BigQuery for eksisterende
konsumenter.

## Anbefalt leserekkefølge

1. Les om [de tre prosesseringsløpene](#tre-prosesseringsløp) for å skille mellom
   å legge til opplysninger, bygge behandlingshistorikk på nytt og publisere
   statistikk på nytt.
2. Følg [lagringen av domenedata](#lagring-av-domenedata-i-postgres) videre til
   [historisk tildeling og statistikkberegning](#historisk-tildeling-og-statistikkberegning).
3. Bruk [oversikten over tidsbegreper](#tidsbegreper) når du sammenligner en
   innkommende hendelse, en lagret historikkrad og publisert statistikk.
4. Følg [leveransen via Datastream](#leveranse-via-datastream) eller
   [direkteleveransen](#direkte-leveranse-til-bigquery) til det aktuelle dataproduktet.

Konsumentkrav og fremgangsmåter for resending er beskrevet i
[systemdokumentasjonen om resending](https://github.com/navikt/aap-intern-sysdok/blob/main/docs/funksjonalitet/09_Statistikk/resendingsfunksjonalitet.md).
De historiske eksemplene der bruker noen eldre Kotlin-navn. Kodelenkene nedenfor
viser til dagens implementasjoner.

## Mottak via API

Hendelser kommer via autentiserte REST POST-kall. Den felles funksjonen i
[mottaStatistikk.kt:41-68](../app/src/main/kotlin/no/nav/aap/statistikk/api/mottaStatistikk.kt#L41-L68)
serialiserer payloaden, legger en Motor-jobb i kø innenfor en transaksjon og
returnerer `202 Accepted`. Svaret betyr at jobben er lagt i kø, ikke at
statistikken er beregnet eller replikert.

|Endpoint                  |Innkommende kontrakt                        |Formål                                                                      |
|--------------------------|--------------------------------------------|----------------------------------------------------------------------------|
|`/stoppetBehandling`      |`StoppetBehandling`                         |Vanlige oppdateringer av behandlinger                                       |
|`/oppdatertBehandling`    |`StoppetBehandling`                         |Bygge behandlingshistorikk på nytt fra opplysninger kilden har sendt på nytt|
|`/oppgave`                |`OppgaveHendelse`                           |Oppgavehistorikk som brukes til tildeling og statistikk                     |
|`/postmottak`             |`DokumentflytStoppetHendelse`               |Oppdateringer fra dokumentbehandling                                        |
|`/tilbakekrevingshendelse`|`TilbakekrevingsbehandlingOppdatertHendelse`|Oppdateringer fra tilbakekrevingsbehandling                                 |

De to endepunktene for `StoppetBehandling` mottar samme kontrakt, men velger
ulike jobber i
[mottaStatistikk.kt:70-113](../app/src/main/kotlin/no/nav/aap/statistikk/api/mottaStatistikk.kt#L70-L113).

## Tre prosesseringsløp

Motor-jobbene registreres i
[App.kt:119-145](../app/src/main/kotlin/no/nav/aap/statistikk/App.kt#L119-L145).
For behandlingshistorikk og saksstatistikk må du skille mellom disse løpene:

|Operasjon                         |Utløses av                                                                               |Jobb og koordinerende tjeneste                                                                               |Viktigste endringer i Postgres                                                                                             |Videre behandling og resultat                                                                                   |
|----------------------------------|-----------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
|Motta nye opplysninger            |POST `/stoppetBehandling`                                                                |`LagreStoppetHendelseJobb` kaller `HendelsesService.prosesserNyHendelse()`                                   |Person-, saks- og behandlingsdata og `behandling_historikk`; meldekort og aktuelle data om avsluttet behandling            |Starter beregning av saksstatistikk. Aktuelle avsluttede behandlinger utløser også direkteleveranse til BigQuery|
|Bygge behandlingshistorikk på nytt|POST `/oppdatertBehandling`                                                              |`RebyggBehandlingshistorikkJobb` kaller `RebyggBehandlingshistorikkService.rebyggHistorikk()`                |Sørger for at person, sak og behandling finnes; invaliderer gamle rader i `behandling_historikk` og setter inn ny historikk|Starter `ResendSakstatistikkJobb`                                                                               |
|Publisere saksstatistikk på nytt  |`SakstatistikkSkalResendes` eller en resending-jobb som settes opp ved en driftsoperasjon|`ResendSakstatistikkJobb` kaller `SaksStatistikkService.alleHendelserPåBehandling()` og `lagreBQBehandling()`|Legger til rader som ikke er duplikater i `saksstatistikk`; endrer ikke behandlingshistorikken                             |Datastream og saksstatistikk-viewene gjør radene tilgjengelige                                                  |

Start med
[HendelsesService.kt:29-69](../app/src/main/kotlin/no/nav/aap/statistikk/hendelser/HendelsesService.kt#L29-L69),
[RebyggBehandlingshistorikkService.kt:38-64](../app/src/main/kotlin/no/nav/aap/statistikk/hendelser/RebyggBehandlingshistorikkService.kt#L38-L64),
og [ResendSakstatistikkJobb.kt:15-25](../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/ResendSakstatistikkJobb.kt#L15-L25).
Publisereren i [MotorHendelsePublisher.kt:26-56](../app/src/main/kotlin/no/nav/aap/statistikk/jobber/appender/MotorHendelsePublisher.kt#L26-L56)
gjør interne `StatistikkHendelse`-verdier om til oppfølgingsjobber.

### Gjenoppbygging er ikke vanlig hendelsesmottak

Gjenoppbyggingen starter med en behandling uten eksisterende historikk.
[BehandlingshistorikkBeregner.kt:12-65](../app/src/main/kotlin/no/nav/aap/statistikk/hendelser/BehandlingshistorikkBeregner.kt#L12-L65)
utleder historiske tilstander fra de resendte endringene i avklaringsbehov og
legger til inngangs- og avslutningshendelser ved behov. Den behandler ikke den
ene resendte DTO-en som én vanlig, ny historikkhendelse.

[BehandlingRepository.kt:255-284](../app/src/main/kotlin/no/nav/aap/statistikk/behandling/BehandlingRepository.kt#L255-L284)
merker tidligere historikkrader med `slettet = TRUE` og `gjeldende = FALSE`,
og setter deretter inn tilstandene som er bygd på nytt. Dette er logisk
invalidering, ikke fysisk sletting. Flyten kjører ikke det vanlige mottaket av
meldekort eller data om avsluttet behandling på nytt.

Kotlin-jobben har fått nytt navn, men den lagrede Motor-typen er fortsatt
`statistikk.lagreAvklaringsbehovHendelseJobb`:
[RebyggBehandlingshistorikkJobb.kt:28-30](../app/src/main/kotlin/no/nav/aap/statistikk/jobber/RebyggBehandlingshistorikkJobb.kt#L28-L30).
Skill mellom disse navnene når du undersøker jobber i kø. En endring av
klassenavnet endrer ikke typen som er lagret på jobbene.

### Ny publisering bruker lagret historikk

Ny publisering krever ikke en ny payload fra Behandlingsflyt.
[SaksStatistikkService.kt:170-196](../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/SaksStatistikkService.kt#L170-L196)
henter lagrede behandlingstilstander og grunnlaget for statistikkberegningen,
beregner nye rader og fletter dem med tidligere publiserte rader. Jobben setter
`erResending = true`, som lagres som `er_relast`, og bruker den vanlige flyten
for å lagre statistikk.

Konsumenten trenger tidligere publiserte funksjonelle tidsstempler for å
identifisere erstatningsdata. Resending må derfor ikke erstatte dem med dagens tid.
[SaksstatistikkHistorikk.kt:7-50](../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/SaksstatistikkHistorikk.kt#L7-L50)
bevarer de gamle `endretTid`-verdiene mens den velger nyberegnet radinnhold,
og fjerner deretter påfølgende duplikater. Garantien om å bevare tidsstemplene
gjelder før denne filtreringen. Hvert gammelt tidsstempel fører derfor ikke
nødvendigvis til en ny lagret rad.
[Systemdokumentasjonen](https://github.com/navikt/aap-intern-sysdok/blob/main/docs/funksjonalitet/09_Statistikk/resendingsfunksjonalitet.md#mekanisme-for-resending)
beskriver konsumentkravet og når det er nødvendig med full resending fra kilden.

## Lagring av domenedata i Postgres

Ved vanlig mottak av behandlingshendelser gjør `HendelsesService.prosesserNyHendelse()`
følgende:

1. Finner eller oppretter person og sak.
2. Oppretter eller oppdaterer behandlingsdata og legger til behandlingshistorikk via
   [BehandlingService.kt:36-51](../app/src/main/kotlin/no/nav/aap/statistikk/hendelser/BehandlingService.kt#L36-L51).
3. Lagrer nye meldekort når de finnes.
4. Kaller `AvsluttetBehandlingService` for avsluttet `Førstegangsbehandling` og
   `Revurdering` for å lagre de aktuelle ytelsesdataene.
5. Publiserer oppfølgingshendelser for de aktuelle statistikkleveransene.

Vanlig lagring velger gjeldende historikkrad etter
`hendelsestidspunkt DESC, oppdatert_tid DESC, id DESC`, ikke bare etter
innsettingsrekkefølge. Se
[BehandlingRepository.kt:151-192](../app/src/main/kotlin/no/nav/aap/statistikk/behandling/BehandlingRepository.kt#L151-L192).
Ikke bland hendelsestid fra kilden med prosesseringstid når du leser spørringen.

Oppgaveflyten har to trinn:
[LagreOppgaveHendelseJobbUtfører.kt:21-37](../app/src/main/kotlin/no/nav/aap/statistikk/oppgave/LagreOppgaveHendelseJobbUtfører.kt#L21-L37)
lagrer innkommende oppgavehendelser og legger `LagreOppgaveJobb` i kø.
[LagreOppgaveJobbUtfører.kt:21-53](../app/src/main/kotlin/no/nav/aap/statistikk/oppgave/LagreOppgaveJobbUtfører.kt#L21-L53)
bygger og lagrer oppgavehistorikk og kan starte beregning av saksstatistikk for
en tilknyttet behandling. Jobben kan også angi et historisk skjæringstidspunkt
fra oppgavehendelsen som brukes som tidsanker.

Kildetabeller i Postgres-skjemaet `public` replikeres til BigQuery-datasettet
`datastream_hendelser`.

## Historisk tildeling og statistikkberegning

`saksbehandler` og `enhet` skal gjenspeile tildelingen på hendelsestidspunktet,
ikke når en jobbutfører tilfeldigvis behandler jobben. Behandlingsoppdateringer
og oppgaveoppdateringer kommer uavhengig av hverandre. Mottaksrekkefølgen alene
kan derfor ikke avgjøre tildelingen.

[SaksStatistikkService.kt:25-80](../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/SaksStatistikkService.kt#L25-L80)
henter et `SaksstatistikkGrunnlag` for behandlingen det beregnes statistikk for:
skjerming, referanse til relatert behandling, oppgaver, rettighetsperioder og
teknisk tid. Ved et eksplisitt skjæringstidspunkt velger tjenesten først
behandlingshistorikk til og med dette tidspunktet.

[SakstatistikkEventSourcing.kt:13-34](../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/SakstatistikkEventSourcing.kt#L13-L34)
samler og sorterer behandlings- og oppgavehendelser for å bygge historiske
tildelingstilstander.
[BQBehandlingMapper.kt:36-73](../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/BQBehandlingMapper.kt#L36-L73)
avgrenser denne tidslinjen til skjæringstidspunktet. Uten et skjæringstidspunkt
brukes den siste tilstanden fra den kombinerte tidslinjen. Ved ny publisering
kalles denne varianten uten skjæringstidspunkt for hver behandlingstilstand.

Noen fallback-regler for tildeling i
[BQBehandlingMapper.kt:259-318](../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/BQBehandlingMapper.kt#L259-L318)
leser fortsatt den nyeste oppgavetilstanden, mens fallback-regelen for siste
løste behov eksplisitt velger historiske oppgavehendelser. Et skjæringstidspunkt
garanterer derfor ikke at alle fallback-reglene bruker historiske data.
Bevar disse forskjellene ved refaktorering. Endringer i fallback-reglene
trenger egne tester av resultatet.

Hvis en rad for en ikke-automatisk behandling mangler ansvarlig enhet,
returnerer vanlig lagring `ManglerEnhet`. Logikken for nye forsøk i
[LagreSakinfoTilBigQueryJobb.kt:64-123](../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/LagreSakinfoTilBigQueryJobb.kt#L64-L123)
beholder skjæringstidspunktet når jobben legges i kø på nytt. Jobben feiler
eksplisitt hvis grensen for antall forsøk er nådd.

## Tidsbegreper

En behandling har flere tidsstempler med ulike betydninger. Feltene er ikke
utbyttbare.

| Felt                                                                                    | Kilde og betydning                                                                                                                                                                                                              | Bruk                                                                                                                                                        |
| --------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `BehandlingHendelse.hendelsesTidspunkt`                                                 | Hendelser på eksisterende behandlinger bruker `StoppetBehandling.hendelsesTidspunkt`. Førstegangslagring og gjenoppbygging har egne regler for å utlede tid fra kilden.                                                         | Funksjonell tidslinje for behandlingen. Repositoriet for historikk skriver `Behandling.oppdatertTidspunkt()` til `behandling_historikk.hendelsestidspunkt`. |
| `Behandling.opprettetTid` / `BQBehandling.registrertTid`                                | `behandlingOpprettetTidspunkt` fra kilden. Statistikk-mapperen trunkerer verdien til hele sekunder.                                                                                                                             | Tidspunktet behandlingen ble opprettet, ikke tidspunktet statistikken ble produsert.                                                                        |
| `Behandling.mottattTid` / `BQBehandling.mottattTid`                                     | `StoppetBehandling.mottattTid` fra kilden, trunkert til hele sekunder i den publiserte raden.                                                                                                                                   | Mottakstid oppgitt av kilden, ikke ankomst til denne appens REST-endpoint.                                                                                  |
| `OppgaveHendelse.sendtTid` / `mottattTidspunkt`                                         | `sendtTid` kommer fra kildens `sendtTidspunkt`. Denne appen setter `mottattTidspunkt` ved mottak.                                                                                                                               | Oppgavetidslinjen bruker `sendtTid`. Sen ankomst gjør ikke kildens tidsstempel nyere.                                                                       |
| `BehandlingHendelse.tidspunkt` ved lesing fra databasen                                 | Hentes fra `behandling_historikk.oppdatert_tid`. Både vanlig lagring og gjenoppbygging av historikk bruker `LocalDateTime.now(clock)`.                                                                                          | Lagringstid i databasen, adskilt fra det funksjonelle historikktidspunktet. Hendelser som ennå ikke er lagret, kan ha null eller syntetiske verdier her.    |
| `BQBehandling.endretTid` / `saksstatistikk.endret_tid` / view-feltet `endretTid`        | I utgangspunktet tidspunktet for siste valgte tilstand i den kombinerte tidslinjen. Det kan komme fra en oppgavehendelse. Lagringen kan justere det, og resending fletter gamle funksjonelle tidsstempler med nyberegnede data. | Publisert funksjonell tid som konsumenten bruker til historikk og erstatning av data.                                                                       |
| `BQBehandling.tekniskTid` / `saksstatistikk.teknisk_tid`                                | `SaksStatistikkService` bruker `LocalDateTime.now(clock)` når grunnlaget for statistikkberegningen hentes.                                                                                                                      | Tidspunktet statistikkraden beregnes, også ved ny publisering. Dette er ikke hendelsestid fra kilden.                                                       |
| Kildetabellenes `oppdatert_tid` / ytelsesstatistikkens `rad_endret_tid` og `endret_tid` | Eksplisitt lagrede tidsstempler for skriving og oppdatering. Hvert view velger de relevante kilderadene. For eksempel tar `view_behandlinger` høyeste tidsstempel fra historikk, diagnose og rettighetstype.                    | Endringssporing for Datastream-baserte ytelsesstatistikk-view, ikke det funksjonelle tidsstempelet i saksstatistikk.                                        |

Se hvordan verdiene mappes i
[BehandlingService.kt:89-153](../app/src/main/kotlin/no/nav/aap/statistikk/hendelser/BehandlingService.kt#L89-L153),
[BehandlingRepository.kt:75-144](../app/src/main/kotlin/no/nav/aap/statistikk/behandling/BehandlingRepository.kt#L75-L144),
[BehandlingRepository.kt:433-438](../app/src/main/kotlin/no/nav/aap/statistikk/behandling/BehandlingRepository.kt#L433-L438),
[mottaStatistikk.kt:143-185](../app/src/main/kotlin/no/nav/aap/statistikk/api/mottaStatistikk.kt#L143-L185),
[SakstatistikkEventSourcing.kt:89-136](../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/SakstatistikkEventSourcing.kt#L89-L136),
[BQBehandlingMapper.kt:167-184](../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/BQBehandlingMapper.kt#L167-L184),
og [view_behandlinger.yml:110-121](../.nais/bigquery/view_behandlinger.yml#L110-L121).

Til tross for navnet velger `Behandling.oppdatertTidspunkt()` funksjonell tid
fra historikken, eller tidspunktet som er utledet for den første tilstanden
når historikken er tom:
[Behandling.kt:143-144](../app/src/main/kotlin/no/nav/aap/statistikk/behandling/Behandling.kt#L143-L144).
Dette er ikke lagringstiden som repositoriet skriver til `oppdatert_tid`.

Statistikklagringen kan legge til ett mikrosekund for å skille rader med samme
funksjonelle tidsstempel. Den kan også opprette en syntetisk inngangsrad. Se
[SaksStatistikkService.kt:82-163](../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/SaksStatistikkService.kt#L82-L163).
Ikke erstatt disse tidene med prosesseringstid for å løse rekkefølgeproblemer.

Kotlin-verdiene er `LocalDateTime`, uten tidssone. Saksstatistikk-viewet tolker
dem eksplisitt som `Europe/Oslo` ved konvertering til BigQuery-tidsstempler:
[view_saksstatistikk.yml:28-38](../.nais/bigquery/view_saksstatistikk.yml#L28-L38).

## Leveranse via Datastream

### Saksstatistikk fra beregnede rader

1. `StatistikkHendelse.SakstatistikkSkalLagres` publiseres.
2. `LagreSakinfoTilBigQueryJobb` kaller `SaksStatistikkService.lagreSaksstatistikk()`.
3. Tjenesten skriver rader som ikke er duplikater til Postgres-tabellen `public.saksstatistikk`.
4. Datastream replikerer tabellen til `datastream_hendelser.public_saksstatistikk`.
5. BigQuery-viewet `saksstatistikk.view_saksstatistikk` gjør datasettet tilgjengelig.

Til tross for navnene på jobben og DTO-en kaller ikke denne flyten BigQuery-klienten.
Følg Postgres-lagringen i
[SakstatistikkRepositoryImpl.kt:35-103](../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/SakstatistikkRepositoryImpl.kt#L35-L103)
og [view_saksstatistikk.yml:20-53](../.nais/bigquery/view_saksstatistikk.yml#L20-L53).
Duplikatsjekken gjør at en vellykket jobb ikke nødvendigvis legger til en rad.

### Ytelsesstatistikk fra replikerte kildetabeller

For avsluttede behandlinger lagres domenedata i Postgres, for eksempel i
`behandling_historikk`, `diagnose`, `diagnose_periode`, `rettighetstype`,
`tilkjent_ytelse*`, `grunnlag*` og `samordning_*`. Datastream replikerer deretter
tabellene til `datastream_hendelser`.

Disse BigQuery-viewene i `ytelsestatistikk` leser de replikerte tabellene:

- `view_behandlinger`
- `view_utbetalinger`
- `view_tilkjent_ytelse`
- `view_vilkarsresultat`
- `view_beregningsgrunnlag`
- `view_samordning`

Viewene bruker lagrede `oppdatert_tid`-felt fra kildetabellene til å beregne
`endret_tid` / `rad_endret_tid`.

### Manuelle rettinger i kildetabeller

Ved manuelle rettinger i Postgres må du oppdatere `oppdatert_tid` eksplisitt i
samme transaksjon som dataendringen. Noen underordnede tabeller har ikke et eget
`oppdatert_tid`-felt. Oppdater da foreldreraden eller en annen rad som blir
stående og brukes av det aktuelle viewet.

Bruk ett tidsstempel for hele rettingen, slik at rader som endres sammen, får
samme `oppdatert_tid`:

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

Hvis flere kildetabeller rettes samtidig, sett `oppdatert_tid` på hver endrede
foreldrerad eller annen rad som blir stående og leses direkte eller indirekte
av et BigQuery-view.

### Manuell sletting

BigQuery-viewene leser bare radene som finnes i de replikerte tabellene. Hvis en
kilderad i en join slettes, kan ikke viewet lese radens gamle
`oppdatert_tid`. Derfor kan `endret_tid` / `rad_endret_tid` forbli uendret eller
gå tilbake til det nest høyeste tidsstempelet blant radene som er igjen.

Ved manuell sletting må du først oppdatere en rad som blir stående og inngår i
viewets beregning av `endret_tid`. Slett deretter radene i samme transaksjon.
For behandlingsrelaterte data er den tryggeste raden normalt behandlingens
gjeldende rad i `behandling_historikk`.

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

Når en underordnet rad slettes og foreldreraden blir stående, kan det være nok
å oppdatere foreldreraden. Oppdater for eksempel tilhørende
`rettighetstype.oppdatert_tid` før du sletter fra `rettighetstypeperioder`.
Hvis foreldreraden også slettes, oppdater en overordnet rad som blir stående,
for eksempel gjeldende `behandling_historikk`.

## Direkte leveranse til BigQuery

`LagreAvsluttetBehandlingTilBigQueryJobb` kaller
`YtelsesStatistikkTilBigQuery.lagre()` og skriver via `BQYtelseRepository`
til tabellen `behandlinger` i BigQuery. Følg
[LagreAvsluttetBehandlingTilBigQueryJobbUtfører.kt:10-30](../app/src/main/kotlin/no/nav/aap/statistikk/avsluttetbehandling/LagreAvsluttetBehandlingTilBigQueryJobbUtfører.kt#L10-L30)
og [BQYtelseRepository.kt:9-15](../app/src/main/kotlin/no/nav/aap/statistikk/bigquery/BQYtelseRepository.kt#L9-L15).

Denne leveransen har fortsatt konsumenter. Den er adskilt fra begge de
Datastream-baserte leveransene ovenfor. Resending av saksstatistikk kjører ikke
denne skrivejobben. Konsumentene må ha bekreftet at de har migrert før
direkteleveransen kan fjernes.

## Deploy av BigQuery-view

View-ressurser under [.nais/bigquery](../.nais/bigquery) deployes av
[deploy_bigquery.yml:21-34](../.github/workflows/deploy_bigquery.yml#L21-L34).

Legg nye view inn i workflowens `RESOURCE`-liste. Legg hvert view før viewene
som er avhengige av det.

## Dataflyten samlet

```text
REST-API (mottaStatistikk.kt)
  -> Motor-kø (JobbAppender)
  -> Jobbutførere
  -> Domenedata og historikktabeller i Postgres
       -> Beregning av saksstatistikk -> Postgres saksstatistikk
       -> Direkte skrivejobb -> BigQuery behandlinger -> Eksisterende konsumenter

Kildetabeller og saksstatistikk i Postgres
  -> Datastream -> datastream_hendelser
  -> BigQuery-view (ytelsestatistikk / saksstatistikk) -> Konsumenter
```
