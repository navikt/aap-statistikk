# Arkitekturforslag: Forenklinger i aap-statistikk

Konkrete forslag til forenklinger, rangert fra størst til minst gevinst.

---

## 1. Fjern alle single-implementasjon interfaces

**Problem:** Kodebasen har to varianter av det samme problemet — interfaces med I-prefiks og
Interface+Impl-splitten — som begge gir et abstraksjonslag uten verdi.

### 1a. I-prefiks interfaces (10 gjenstår)

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

### 1b. Interface+Impl-split uten verdi (11 gjenstår)

| Interface                              | Implementasjon                            |
| -------------------------------------- | ----------------------------------------- |
| `DiagnoseRepository`                   | `DiagnoseRepositoryImpl`                  |
| `EnhetRepository`                      | `EnhetRepositoryImpl`                     |
| `SaksbehandlerRepository`              | `SaksbehandlerRepositoryImpl`             |
| `ArbeidsopptrappingperioderRepository` | `ArbeidsopptrappingperioderRepositoryImpl`|
| `FritaksvurderingRepository`           | `FritaksvurderingRepositoryImpl`          |
| `OppgaveHendelseRepository`            | `OppgaveHendelseRepositoryImpl`           |
| `OppgaveRepository`                    | `OppgaveRepositoryImpl`                   |
| `PostmottakBehandlingRepository`       | `PostmottakBehandlingRepositoryImpl`      |
| `SakRepository`                        | `SakRepositoryImpl`                       |
| `SakstatistikkRepository`              | `SakstatistikkRepositoryImpl`             |
| `TilbakekrevingHendelseRepository`     | `TilbakekrevingHendelseRepositoryImpl`    |

**Forslag:** Slett interface-filene, gi Impl-klassene det rene navnet (uten `Impl`). Der tester
trenger en test-dobbel, bruk en in-memory fake — ikke interface.

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

**Forslag:** Flytt logikken direkte inn i repository-klassen (`hentEllerLagre`-metoder hører
naturlig hjemme der), eller inline der det kalles.

```kotlin
// Etter: logikken i repository
class PersonRepository(...) {
    fun hentEllerLagre(ident: String): Person { ... }
}
```

I tillegg har `SakService` og `PersonService` sekundærkonstruktører som tar `RepositoryProvider`
for DI-kobling. Når service-laget forsvinner, forsvinner disse også.

**Gevinst:** Fjerner 3 klasser og 3 ekstra lag i call-stacks. `HendelsesService` og
`LagreStoppetHendelseJobb` forenkles tilsvarende (færre konstruktørparametere).

---

## 3. Dedupliser SaksStatistikkService-konstruksjon

**Problem:** `LagreSakinfoTilBigQueryJobb.konstruer()` og `ResendSakstatistikkJobb.konstruer()`
konstruerer `SaksStatistikkService` med nøyaktig samme kode:

```kotlin
// LagreSakinfoTilBigQueryJobb.konstruer() — linjer 111-123
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

**Forslag:** Trekk ut en privat hjelpefunksjon (f.eks. i en `SaksStatistikkFactory.kt`) eller
legg den som en statisk funksjon:

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

**Problem:** Begge klasser instansierer `MotorJobbAppender()` direkte inne i metoden:

```kotlin
// LagreOppgaveJobbUtfører.kt
MotorJobbAppender().leggTilLagreSakTilBigQueryJobb(repositoryProvider, it.id(), triggerKilde = "oppgave")

// LagreSakinfoTilBigQueryJobb.kt
return LagreSakinfoTilBigQueryJobbUtfører(sakStatistikkService, MotorJobbAppender(), repositoryProvider)
```

**Forslag:** Injiser `JobbAppender` via konstruktøren — slik `LagreStoppetHendelseJobb` allerede
gjør.

**Gevinst:** Klassene blir testbare og konsistente med resten av kodebasen.

---

## Oppsummering

| #   | Tiltak                                                         | Filer fjernet/forenklet | Vanskelighetsgrad |
| --- | -------------------------------------------------------------- | ----------------------- | ----------------- |
| 1   | Fjern alle single-implementasjon interfaces (~21 stk)          | ~21 filer               | Middels           |
| 2   | Fjern tynne wrapper-services (PersonService, SakService, Post) | 3 klasser               | Middels           |
| 3   | Dedupliser SaksStatistikkService-konstruksjon                  | 2 steder → 1            | Lav               |
| 4   | Injiser JobbAppender i oppgave- og saksinfo-jobber             | 2 filer                 | Lav               |

