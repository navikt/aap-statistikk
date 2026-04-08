# Oppfølging etter PR #741

## Bakgrunn

PR #741 ga implementasjonsklassene `Impl`-suffiks (f.eks. `BehandlingRepository` → `BehandlingRepositoryImpl`).
Interface-filene ble beholdt med `I`-prefiks for å holde PR-diff-en liten.

## Neste steg: Fjern `I`-prefiks fra interfaces

Gi interface-filene nye navn uten `I`-prefiks. Siden implementasjonsklassene nå heter `*Impl`,
vil disse omdøpingene vises som rene renames i GitHub (ingen path-kollisjon).

### Filer som skal omdøpes

| Fra | Til |
|-----|-----|
| `IBehandlingRepository.kt` | `BehandlingRepository.kt` |
| `IBeregningsgrunnlagRepository.kt` | `BeregningsgrunnlagRepository.kt` |
| `IBigQueryClient.kt` | `BigQueryClient.kt` |
| `IBQYtelsesstatistikkRepository.kt` | `BQYtelsesstatistikkRepository.kt` |
| `IMeldekortRepository.kt` | `MeldekortRepository.kt` |
| `IPersonRepository.kt` | `PersonRepository.kt` |
| `ISaksStatistikkService.kt` | `SaksStatistikkService.kt` |
| `ITilkjentYtelseRepository.kt` | `TilkjentYtelseRepository.kt` |
| `IVilkårsresultatRepository.kt` | `VilkårsresultatRepository.kt` |
| `IRettighetstypeperiodeRepository.kt` | `RettighetstypeperiodeRepository.kt` |

### I tillegg

- Rename interface-navn inne i filene (f.eks. `interface IBehandlingRepository` → `interface BehandlingRepository`)
- Oppdater alle type-annoteringer og imports som bruker `IFoo`-navnene
- `PersonRepositoryImpl` ligger i dag i `IPersonRepository.kt` — vurder å flytte den til en egen fil

---

## Vurder å erstatte callbacks med en JobbAppender-abstraksjon

Flere service-klasser tar i dag inn callbacks som konstruktør-parametere for å trigge bakgrunnsjobber:

```kotlin
// HendelsesService
private val opprettBigQueryLagringSakStatistikkCallback: (BehandlingId) -> Unit

// AvsluttetBehandlingService
private val opprettBigQueryLagringYtelseCallback: (BehandlingId) -> Unit

// ResendHendelseService
private val opprettRekjørSakstatistikkCallback: (BehandlingId) -> Unit
```

**Problemet** med denne tilnærmingen:
- Callback-logikken (hvilken jobb som legges til, med hvilken delay, osv.) lekker inn i service-klassene via konstruktøren
- Testene må mocke/fake callbacks selv om de egentlig bare ønsker å teste forretningslogikken
- Det er uklart fra signaturen hva callbacken faktisk gjør

**Alternativ:** Bruk `JobbAppender` direkte i service-klassene i stedet for en generisk callback:

```kotlin
class HendelsesService(
    ...
    private val jobbAppender: JobbAppender,  // i stedet for callback
) {
    fun prosesserNyHendelse(hendelse: StoppetBehandling) {
        ...
        jobbAppender.leggTilLagreSakTilBigQueryJobb(repositoryProvider, behandlingId)
    }
}
```

Dette gjør koden mer eksplisitt, lettere å forstå og enklere å teste (man kan bruke en fake `JobbAppender`).
