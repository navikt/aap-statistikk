# Arkitektur- og forenklingsgjennomgang

Dette dokumentet oppsummerer observasjoner og forslag til forenkling av kodebasen, uten å endre funksjonalitet.

Kodebasen består av 173 Kotlin-filer (ca. 123 produksjonskode, 50 tester) og er godt strukturert med klare domeneinndelinger. Det er likevel en del mønstre som kan forenkles for bedre lesbarhet og vedlikeholdbarhet.

---

## 1. Fjern alle single-implementasjon interfaces

**Problem:** Kodebasen har to varianter av det samme problemet — interfaces med I-prefiks og
Interface+Impl-splitten — som begge gir et abstraksjonslag uten verdi.

### 1a. I-prefiks interfaces (10 stk)

| Interface                          | Implementasjon                    |
| ---------------------------------- | --------------------------------- |
| `IBehandlingRepository`            | `BehandlingRepository`            |
| `ISaksStatistikkService`           | `SaksStatistikkService`           |
| `IBeregningsgrunnlagRepository`    | `BeregningsgrunnlagRepository`    |
| `IVilkårsresultatRepository`       | `VilkårsresultatRepository`       |
| `ITilkjentYtelseRepository`        | `TilkjentYtelseRepository`        |
| `IPersonRepository`                | `PersonRepository`                |
| `IRettighetstypeperiodeRepository` | `RettighetstypeperiodeRepository` |
| `IMeldekortRepository`             | `MeldekortRepository`             |
| `IBQYtelsesstatistikkRepository`   | `BQYtelseRepository`              |
| `IBigQueryClient`                  | `BigQueryClient`                  |

### 1b. Interface+Impl-split uten verdi (11 stk)

| Interface                              | Implementasjon                             |
| -------------------------------------- | ------------------------------------------ |
| `DiagnoseRepository`                   | `DiagnoseRepositoryImpl`                   |
| `EnhetRepository`                      | `EnhetRepositoryImpl`                      |
| `SaksbehandlerRepository`              | `SaksbehandlerRepositoryImpl`              |
| `ArbeidsopptrappingperioderRepository` | `ArbeidsopptrappingperioderRepositoryImpl` |
| `FritaksvurderingRepository`           | `FritaksvurderingRepositoryImpl`           |
| `OppgaveHendelseRepository`            | `OppgaveHendelseRepositoryImpl`            |
| `OppgaveRepository`                    | `OppgaveRepositoryImpl`                    |
| `PostmottakBehandlingRepository`       | `PostmottakBehandlingRepositoryImpl`       |
| `SakRepository`                        | `SakRepositoryImpl`                        |
| `SakstatistikkRepository`              | `SakstatistikkRepositoryImpl`              |
| `TilbakekrevingHendelseRepository`     | `TilbakekrevingHendelseRepositoryImpl`     |

**Forslag:** Slett interface-filene og gi Impl-klassene det rene navnet (uten `Impl`). Der tester
trenger en test-dobbel, bruk en in-memory fake — ikke interface. `VedtattStansOpphørRepository` og
`PostmottakBehandlingRepository` er i tillegg definert i impl-filen sin, noe som er ekstra
forvirrende.

```kotlin
// Før: to filer, to typer, ingenting å vinne
interface DiagnoseRepository : Repository { fun lagre(...); fun hent(...) }
class DiagnoseRepositoryImpl(private val dbConnection: DBConnection) : DiagnoseRepository { ... }

// Etter: én fil, én klasse
class DiagnoseRepository(private val dbConnection: DBConnection) : Repository { ... }
```

**Gevinst:** Fjerner ~21 filer, eliminerer dobbel navigering (Ctrl+Click → interface → Impl),
gjør det enklere å legge til metoder.

---

## 2. Fjern tynne wrapper-services

**Problem:** `PersonService`, `SakService` og `PostmottakBehandlingService` er klasser med én
metode som kun videresender til et repository. De gir ingen ekstra verdi, men krever at man hopper
gjennom et ekstra lag for å lese koden.

**`PersonService`** (én metode):

```kotlin
class PersonService(private val personRepository: IPersonRepository) {
    fun hentEllerLagrePerson(ident: String): Person { ... }  // Kaller bare repository
}
```

**`SakService`** (én metode):

```kotlin
class SakService(private val sakRepository: SakRepository) {
    fun hentEllerSettInnSak(person: Person, saksnummer: Saksnummer, sakStatus: SakStatus): Sak { ... }
}
```

**`PostmottakBehandlingService`** (én metode):

```kotlin
class PostmottakBehandlingService(private val postmottakBehandlingRepository: PostmottakBehandlingRepository) {
    fun oppdaterEllerOpprettBehandling(innkommendeBehandling: PostmottakBehandling): PostmottakBehandling { ... }
}
```

**Forslag:** Flytt logikken direkte inn i repository-klassen — `hentEllerLagre`-metoder hører
naturlig hjemme der — eller inline der det kalles. `SakService` og `PersonService` har i tillegg
sekundærkonstruktører som tar `RepositoryProvider` for DI-kobling; disse forsvinner også.

**Gevinst:** Fjerner 3 klasser og 3 ekstra lag i call-stacks. `HendelsesService` og
`LagreStoppetHendelseJobb` forenkles tilsvarende med færre konstruktørparametere.

---

## 3. Dedupliser SaksStatistikkService-konstruksjon

**Problem:** `LagreSakinfoTilBigQueryJobb.konstruer()` og `ResendSakstatistikkJobb.konstruer()`
konstruerer `SaksStatistikkService` med nøyaktig samme kode:

```kotlin
// LagreSakinfoTilBigQueryJobb.konstruer()
val behandlingService = BehandlingService(repositoryProvider, gatewayProvider)
val sakStatistikkService = SaksStatistikkService(
    behandlingService = behandlingService,
    sakstatistikkRepository = repositoryProvider.provide(),
    bqBehandlingMapper = BQBehandlingMapper(
        behandlingService = behandlingService,
        rettighetstypeperiodeRepository = repositoryProvider.provide(),
        oppgaveRepository = repositoryProvider.provide(),
        sakstatistikkEventSourcing = SakstatistikkEventSourcing(),
    ),
)

// ResendSakstatistikkJobb.konstruer() — nøyaktig samme kode
```

**Forslag:** Trekk ut en hjelpefunksjon:

```kotlin
private fun lagSaksStatistikkService(
    repositoryProvider: RepositoryProvider,
    gatewayProvider: GatewayProvider,
): SaksStatistikkService {
    val behandlingService = BehandlingService(repositoryProvider, gatewayProvider)
    return SaksStatistikkService(
        behandlingService = behandlingService,
        sakstatistikkRepository = repositoryProvider.provide(),
        bqBehandlingMapper = BQBehandlingMapper(
            behandlingService = behandlingService,
            rettighetstypeperiodeRepository = repositoryProvider.provide(),
            oppgaveRepository = repositoryProvider.provide(),
            sakstatistikkEventSourcing = SakstatistikkEventSourcing(),
        ),
    )
}
```

**Gevinst:** Endringer i oppbygging av `SaksStatistikkService` gjøres ett sted.

---

## 4. Injiser JobbAppender i LagreOppgaveJobbUtfører og LagreSakinfoTilBigQueryJobb

**Problem:** Begge klasser instansierer `MotorJobbAppender()` direkte inne i metoden i stedet for
å motta den via konstruktøren:

```kotlin
// LagreOppgaveJobbUtfører.kt
MotorJobbAppender().leggTilLagreSakTilBigQueryJobb(repositoryProvider, it.id(), triggerKilde = "oppgave")

// LagreSakinfoTilBigQueryJobb.kt
return LagreSakinfoTilBigQueryJobbUtfører(sakStatistikkService, MotorJobbAppender(), repositoryProvider)
```

**Forslag:** Injiser `JobbAppender` via konstruktøren — slik `LagreStoppetHendelseJobb` allerede
gjør. Se også punkt 6 om den skjulte avhengigheten til `postgresRepositoryRegistry` i
`MotorJobbAppender`.

**Gevinst:** Klassene blir testbare og konsistente med resten av kodebasen.

---

## 5. TestUtils.kt er en monolitt på 1035 linjer

**Problem:** `testutils/TestUtils.kt` er en "alt mulig"-fil med:

- Fake-implementasjoner av 12+ repositories (`FakeSakRepository`, `FakePersonRepository`, `FakeBehandlingRepository`, `FakeBQYtelseRepository`, osv.)
- Tre varianter av test-klient-oppsett (`testKlient`, `testKlientNoInjection`, `testKlientNoInjectionManuell`)
- Motor-fabrikk-hjelpere (`konstruerMotor`, `konstruerManuellMotor`, `konstruerTestJobber`)
- Test-datahjelpere (`opprettTestPerson`, `opprettTestSak`, `opprettTestBehandling`, `opprettTestHendelse`)
- `MockJobbAppender`, `noOpTransactionExecutor`, `motorMock`
- BigQuery-relatert testkode (duplisert av `WithBigQueryContainer.kt`)

Alle tester i prosjektet har en transitiv kobling til én enkelt fil, noe som gjør det vanskelig å finne frem og øker risikoen for utilsiktede sideeffekter.

**Forslag:** Del filen opp langs domene- og ansvarslinjer:

```
testutils/
  fakes/
    FakeRepositories.kt       -- alle Fake*Repository-klasser
    FakeSaksstatistikkService.kt
  builders/
    TestDataBuilders.kt       -- opprettTestPerson, opprettTestSak, osv.
  motor/
    MotorTestHelpers.kt       -- konstruerMotor, motorMock, osv.
  client/
    TestClientHelpers.kt      -- testKlient og varianter
```

`FakeHendelsePublisher.kt` er allerede riktig isolert – den er et godt eksempel på riktig granularitet.

---

## 6. `IBQYtelsesstatistikkRepository` er markert som `@Deprecated` men fortsatt i aktiv bruk

**Problem:** Interfacet er annotert med `@Deprecated("Vil bli fjernet såsnart Team Spenn går over til å bruke view_behandlinger.")` – men er fortsatt brukt i produksjonskode og tester (`FakeBQYtelseRepository`, `BQYtelseRepository`).

```kotlin
// bigquery/IBQYtelsesstatistikkRepository.kt
@Deprecated("Vil bli fjernet...")
interface IBQYtelsesstatistikkRepository {
    fun lagre(payload: BQYtelseBehandling)
    fun start()
    fun commit()
}
```

`start()` og `commit()` er "Unit of Work"-metodekall som holder mutable tilstand (`valsToCommit`) inne i `BQYtelseRepository`. Mønsteret er unødvendig komplekst for det det gjør.

**Forslag:** Avklar om Team Spenn har fullført overgangen. Om ja, fjern hele interfacet og `BQYtelseRepository`-klassen, og fjern alle tilhørende `FakeBQYtelseRepository`-forekomster.

---

## 7. `MotorJobbAppender` refererer til en global singleton

**Problem:** `MotorJobbAppender.leggTil(connection, jobb)` refererer direkte til det globale `postgresRepositoryRegistry`-objektet:

```kotlin
override fun leggTil(connection: DBConnection, jobb: JobbInput) {
    leggTil(postgresRepositoryRegistry.provider(connection), jobb)
}
```

Dette gjør klassen vanskelig å teste isolert og introduserer en skjult avhengighet.

**Forslag:** Injiser `RepositoryRegistry` via konstruktøren, eller fjern den ene overloaden og kall alltid med `RepositoryProvider`.

---

## 8. `JobbAppender`-interfacet er et "fat interface"

**Problem:** `JobbAppender` har 5 metoder med svært spesifikk domenekunnskap om hvilke jobber som finnes:

```kotlin
interface JobbAppender {
    fun leggTil(connection: DBConnection, jobb: JobbInput)
    fun leggTil(repositoryProvider: RepositoryProvider, jobb: JobbInput)
    fun leggTilLagreSakTilBigQueryJobb(...)
    fun leggTilLagreAvsluttetBehandlingTilBigQueryJobb(...)
    fun leggTilResendSakstatistikkJobb(...)
}
```

Hver gang en ny jobbtype introduseres, må `JobbAppender`, `MotorJobbAppender` og `MockJobbAppender` alle oppdateres.

**Forslag:** Bruk den generelle `leggTil(repositoryProvider, jobbInput)`-metoden som eneste API, og flytt de jobbspesifikke metodene til egne hjelpeobjekter eller inn i de respektive jobb-klassene. `MockJobbAppender` kan da forenkles til å bare inspisere hvilke `JobbInput`-objekter som ble lagt til.

---

## 9. `MotorHendelsePublisher` har et `HACKY_DELAY`-miljøvariabel lest ad hoc

**Problem:** I `MotorHendelsePublisher.kt` leses en miljøvariabel direkte inne i forretningslogikken:

```kotlin
delayInSeconds = System.getenv("HACKY_DELAY")?.toLong() ?: 0L,
```

Variabelen er kommentert som «Veldig hacky!» og er et midlertidig plaster på en race condition mellom oppgave-appen og statistikk-appen. Den eksisterer i `nais.yaml` og er lest spredt i koden.

**Forslag:** Flytt alle `System.getenv`-kall til `AppConfig` eller `DbConfig` slik at all konfigurasjon er samlet ett sted. Dette gjør det også mulig å finne alle konfigurasjonsverdier i én gjennomlesning.

---

## 10. Tre varianter av test-klient-oppsett

**Problem:** `TestUtils.kt` eksponerer tre ulike funksjoner for å sette opp en test-klient mot en lokal HTTP-server:

- `testKlient(...)` – bruker Motor og Ktor-server
- `testKlientNoInjection(...)` – bruker ManuellMotor
- `testKlientNoInjectionManuell(...)` – ytterligere en variant

Disse er svært like men med subtile forskjeller i Motor-oppsettet. Det er ikke tydelig fra navnene hva forskjellen er, og alle tre brukes i produksjonstester.

**Forslag:** Slå sammen til én konfigurerbar funksjon, eller innfør et testoppsett-objekt som uttrykker valgene eksplisitt:

```kotlin
testKlient(motorType = MotorType.Manuell) { ... }
```

---

## 11. `IntegrationTest.kt` er 1032 linjer

**Problem:** Én enkelt testfil med 1032 linjer som tester veldig mange urelaterte flyter (oppgave, postmottak, tilbakekreving, saksstatistikk, osv.). Tester som feiler er vanskelige å isolere, og filen er vanskelig å orientere seg i.

**Forslag:** Del opp i separate testklasser per flyt, f.eks. `OppgaveIntegrationTest`, `PostmottakIntegrationTest`, `SaksstatistikkIntegrationTest`. Felles oppsett (Postgres, fakes) kan trekkes ut til en felles base-klasse eller JUnit 5-extension.

---

## 12. `LagreSakinfoTilBigQueryJobb` inneholder bakoverkompatibilitets-deserialiseringshack

**Problem:** `lesPayload()`-metoden i `LagreSakinfoTilBigQueryJobbUtfører` inneholder:

```kotlin
// TODO: Fjern bakoverkompatibilitet etter at alle gamle jobber er prosessert
private fun lesPayload(input: JobbInput): LagreSakinfoPayload {
    return try {
        input.payload<LagreSakinfoPayload>()
    } catch (e: DeserializationException) {
        // ... fallback til gammelt format
    }
}
```

**Forslag:** Sjekk om det fortsatt finnes gamle jobber i Motor-tabellen. Om ikke, fjern try/catch-blokken og det gamle formatet. Lar man slik kode bli stående lenge, øker risikoen for at den aldri fjernes.

---

## 13. Kommentar igjen i produksjonskode

**Problem:** Nederst i `MotorJobbAppender.kt` finnes en ufullstendig/midlertidig kommentar:

```kotlin
// rad endret og og behandling avsluttet-tid mangler i view
// hvis fritak fra meldekort-status, dele tidslinjen
```

**Forslag:** Flytt til en GitHub-issue. Kommentarer som beskriver fremtidig arbeid hører ikke hjemme i produksjonskode.

---

## Oppsummering

| #   | Funn                                                           | Innsats                  | Effekt                         |
| --- | -------------------------------------------------------------- | ------------------------ | ------------------------------ |
| 1   | Fjern ~21 single-implementasjon interfaces (I-prefiks + Impl)  | Middels                  | Høy – færre filer, enklere nav |
| 2   | Fjern tynne wrapper-services (PersonService, SakService, Post) | Middels                  | Middels – kortere call-stacks  |
| 3   | Dedupliser SaksStatistikkService-konstruksjon                  | Lav                      | Lav – ett sted å endre         |
| 4   | Injiser JobbAppender i oppgave- og saksinfo-jobber             | Lav                      | Middels – testbarhet           |
| 5   | TestUtils.kt monolitt (1035 linjer)                            | Middels                  | Høy – testlesbarhet            |
| 6   | Deprecated `IBQYtelsesstatistikkRepository` i aktiv bruk       | Lav (avklar, slett)      | Middels – ryddigere kodebase   |
| 7   | Skjult avhengighet til global `postgresRepositoryRegistry`     | Lav                      | Middels – testbarhet           |
| 8   | Fat `JobbAppender`-interface                                   | Middels                  | Middels – utvidbarhet          |
| 9   | `HACKY_DELAY` lest ad hoc i forretningslogikk                  | Lav                      | Lav – ryddigere konfig         |
| 10  | Tre ulike test-klient-varianter                                | Middels                  | Middels – testlesbarhet        |
| 11  | `IntegrationTest.kt` på 1032 linjer                            | Middels                  | Høy – testlesbarhet            |
| 12  | Bakoverkompatibilitets-hack i payload-deserialisering          | Lav (etter verifisering) | Lav – ryddigere kode           |
| 13  | Midlertidige kommentarer i produksjonskode                     | Svært lav                | Lav – ryddigere kode           |

De største gevinstene ligger i **#1** (fjern 21 interfaces), **#5** (del opp TestUtils.kt) og **#11**
(del opp IntegrationTest.kt). Disse kan gjøres uavhengig av hverandre.
