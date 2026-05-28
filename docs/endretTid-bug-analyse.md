# Analyse: «Ny hendelse med samme endretTid» – Årsaker og feilkilder

## Oversikt

Logginjen `"Ny hendelse med samme endretTid. Forrige teknisk tid: …"` vises i
`SaksStatistikkService.tilpassEndretTid()`. Den utløses når to `BQBehandling`-hendelser
beregner nøyaktig samme `endretTid`. Koden «bumper» da den nye hendelsens `endretTid` med
+1 000 ns slik at rekkefølgen bevares i BigQuery.

Problemet er at denne bumpingen **ikke tar hensyn til hva slags status de to hendelsene
har**. Hvis den allerede lagrede hendelsen er `AVSLUTTET` og den inkommende (bumpede)
hendelsen er f.eks. `UNDER_BEHANDLING`, vil `UNDER_BEHANDLING` bli lagret _etter_
`AVSLUTTET`. Det bryter kravet om at `AVSLUTTET` alltid skal komme sist i historikken.

---

## Produksjonsdata (siste 30 dager)

| Logghendelse                                          | Antall     | Vurdering                                   |
| ----------------------------------------------------- | ---------- | ------------------------------------------- |
| `Ny hendelse med samme endretTid`                     | **3 514**  | De fleste er normalt overlappende jobber    |
| `Feil rekkefølge: lagrer IVERKSETTES etter AVSLUTTET` | **0**      | Sjekken dekker feil status — se Feilkilde 2 |
| `Reschedulerer om` (ManglerEnhet retry planlagt)      | **16 801** | Meget aktiv                                 |
| `triggerKilde=retry(...)` (retry kjørt)               | **16 922** | ≈ 1:1 med Reschedulerer                     |
| `Enhet mangler fortsatt` (alle retries oppbrukt)      | **116**    | Mislyktes etter 3 forsøk                    |
| **AVSLUTTET → UNDER_BEHANDLING** (feil rekkefølge)    | **~300**   | 🔴 **Bekreftet bug — ~10/dag**              |

### Status-kombinasjoner i «Ny hendelse med samme endretTid» (stikkprøve, 500 linjer)

| Forrige status → Ny status          | Antall         | Vurdering                                              |
| ----------------------------------- | -------------- | ------------------------------------------------------ |
| IVERKSETTES → AVSLUTTET             | 383 (76,6 %)   | ✅ Normal — AVSLUTTET ankommer ~20 s etter IVERKSETTES |
| IVERKSETTES → IVERKSETTES           | 87 (17,4 %)    | ✅ Normal — duplikatjobber fra samtidige triggere      |
| **AVSLUTTET → UNDER_BEHANDLING**    | **22 (4,4 %)** | 🔴 Bug — frossen retry etter AVSLUTTET                 |
| UNDER_BEHANDLING → UNDER_BEHANDLING | 3              | Duplikat                                               |
| IVERKSETTES → UNDER_BEHANDLING      | 2              | Mulig bug                                              |
| AVSLUTTET → IVERKSETTES             | 0              | Antas i hypotesen — ses ikke i prod                    |

### Bekreftet race condition — komplett produksjonstidslinje

```
13:05:47.757  triggerKilde=oppgave, retryCount=0
              status=UNDER_BEHANDLING, endretTid=13:05:47.529659
              ❌ ManglerEnhet for VURDER_TREKK_AV_SØKNAD
              → Reschedulerer om 60s (forsøk 1/3)

13:06:07.860  triggerKilde=behandling, retryCount=0
              status=AVSLUTTET, endretTid=13:05:47.529659   ← SAMME endretTid
              ✅ AVSLUTTET lagret i saksstatistikk

13:06:47.997  triggerKilde=retry(oppgave), retryCount=1
              Finner: siste rad er AVSLUTTET med endretTid=13:05:47.529659
              Ny rad: UNDER_BEHANDLING med SAMME endretTid=13:05:47.529659
              → «Ny hendelse med samme endretTid. Forrige status: AVSLUTTET»
              → Bumpes med +1µs
              → UNDER_BEHANDLING lagres ETTER AVSLUTTET i BigQuery 🔴
```

**Timing** (100 tilfeller): AVSLUTTET ankommer systematisk **~20 s** (median 20,2 s) etter
at UNDER_BEHANDLING-jobben fikk ManglerEnhet — retry-forsinkelsen på 60 s gir rikelig tid
til at AVSLUTTET rekker å bli lagret først.

---

## Flyt – oversikt

```
StoppetBehandling (REST POST)
    └─► LagreStoppetHendelseJobb
            └─► HendelsesService.prosesserNyHendelse()
                    ├── Lagrer/oppdaterer Behandling i Postgres
                    └── Publiserer StatistikkHendelse.SakstatistikkSkalLagres
                             └─► LagreSakinfoTilBigQueryJobb
                                     └─► SaksStatistikkService.lagreSakInfoTilBigquery()
                                             ├── bqBehandlingMapper.bqBehandlingForBehandling()
                                             │       └── SakstatistikkEventSourcing.byggSakstatistikkHendelser()
                                             │               → sorter behandlings- og oppgavehendelser
                                             │               → endretTid = snapshots.last().tidspunkt
                                             └── lagreBQBehandling()
                                                     ├── hentSisteHendelseForBehandling()
                                                     ├── ansesSomDuplikat()?
                                                     ├── [opprett inngangs-OPPRETTET-rad om nødvendig]
                                                     └── tilpassEndretTid() → lagre
```

---

## Kilde til `endretTid`

`endretTid` beregnes i `BQBehandlingMapper.bqBehandlingForBehandling()`:

```kotlin
val snapshots = sakstatistikkEventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)
val endretTid = snapshots.last().tidspunkt   // <-- kilde
```

`byggSakstatistikkHendelser()` slår sammen **behandlingshendelser** (fra
`behandling.hendelser`) og **oppgavehendelser** (fra `oppgaveRepository`) og sorterer dem
kronologisk. `endretTid` er altså tidspunktet for den _siste_ hendelsen i det sammenslåtte
forløpet, enten fra behandlingsflyt eller fra oppgavesystemet.

**Nøkkelkonsekvens:** Dersom oppgavens `LUKKET`-hendelse er den seneste hendelsen i
sekvensen for _begge_ tilstandene (UNDER_BEHANDLING og AVSLUTTET), vil begge beregne
**samme `endretTid`**. Dette er strukturelt uunngåelig med nåværende logikk og bekreftes av
at AVSLUTTET ankommer med nøyaktig samme `endretTid` som den frosne UNDER_BEHANDLING-snapshoten.

---

## Feilkilde 1 – ManglerEnhet-retry med frossen snapshot ✅ Bekreftet i prod

### Sekvens

```
1. UNDER_BEHANDLING-melding mottas, LagreSakinfoTilBigQueryJobb kjøres
   → bqBehandlingMapper beregner: behandlingStatus=UNDER_BEHANDLING, endretTid=T
   → lagreSakInfoTilBigquery() returnerer ManglerEnhet
   → Ny retry-jobb planlegges med storedBQBehandling = UNDER_BEHANDLING@T  ← frossen snapshot
   → Retry-forsinkelse: 60 s (forsøk 1), 120 s (forsøk 2), 240 s (forsøk 3)

2. ~20 s etter: AVSLUTTET-melding ankommer og behandles
   → LagreSakinfoTilBigQueryJobb kjøres
   → bqBehandlingMapper beregner: behandlingStatus=AVSLUTTET, endretTid=T  ← SAMME T
     (fordi oppgave-LUKKET-hendelse er siste i snapshots for begge tilstandene)
   → AVSLUTTET lagres i saksstatistikk med endretTid=T

3. ~60 s etter steg 1: Retry-jobben kjøres (lagreMedStoredBQBehandling)
   → storedBQBehandling = UNDER_BEHANDLING@T  (frossen fra steg 1)
   → hentSisteHendelseForBehandling() → AVSLUTTET@T
   → ansesSomDuplikat(UNDER_BEHANDLING) → false  (ulik behandlingStatus)
   → tilpassEndretTid(): siste.endretTid == bqSak.endretTid (begge T)
        → logger "Ny hendelse med samme endretTid. Forrige status: AVSLUTTET"
        → bumper UNDER_BEHANDLING til T+1000ns

4. UNDER_BEHANDLING lagres med endretTid=T+1000ns — ETTER AVSLUTTET@T
   → AVSLUTTET er ikke lenger siste rad i historikken  ✗
   → Skjer ~10 ganger per dag i produksjon
```

### Hvorfor UNDER_BEHANDLING og ikke IVERKSETTES?

ManglerEnhet oppstår typisk på behandlinger i aktiv saksbehandling (`UNDER_BEHANDLING` med
avklaringsbehov som `VURDER_TREKK_AV_SØKNAD`). IVERKSETTES-fasen er kortere og enhet er
da som regel satt. Produksjonsloggene bekrefter: alle 300 feiltilfeller er
`AVSLUTTET → UNDER_BEHANDLING`, ingen er `AVSLUTTET → IVERKSETTES`.

### Kode det gjelder

- `LagreSakinfoTilBigQueryJobb.utførJobb()`: planlegger retry med `storedBQBehandling`
- `SaksStatistikkService.lagreMedStoredBQBehandling()`: bruker frossen
  `storedBQBehandling.copy(ansvarligEnhetKode = enhet, ...)`
- `SaksStatistikkService.tilpassEndretTid()`, grenen `siste.endretTid == bqSak.endretTid`

---

## Feilkilde 2 – Bumplogikk uten statusvakt + blindt hull i feilsjekk

`tilpassEndretTid()` bumper blindt:

```kotlin
siste.endretTid == bqSak.endretTid -> {
    log.info("Ny hendelse med samme endretTid …")
    PrometheusProvider.prometheus.sammeEndretTid().increment()
    bqSak.copy(endretTid = siste.endretTid.plusNanos(1000))   // ← ingen statussjekk
}
```

`lagreBQBehandling()` har en delvis sjekk (linje ~118):

```kotlin
if (siste?.behandlingStatus == "AVSLUTTET" && bqSakMedUnikEndretTid.behandlingStatus == "IVERKSETTES") {
    log.error("Feil rekkefølge: lagrer IVERKSETTES etter at AVSLUTTET allerede er lagret …")
}
```

**To mangler — begge bekreftet av produksjondata:**

1. Hendelsen **lagres likevel** — `log.error` er kun logging, ingen blokkering.
2. Sjekken dekker kun `IVERKSETTES`. Feilen skjer i prod med `UNDER_BEHANDLING` — denne
   logglinjen har aldri blitt utløst (`count = 0` siste 30 dager).

---

## Feilkilde 3 – Samme `endretTid` for UNDER_BEHANDLING og AVSLUTTET

**Mekanisme:** Oppgavens `LUKKET`-hendelse (`sendtTid = T`) er den siste hendelsen i
event-sourcing-rekken for begge tilstandene fordi AVSLUTTET-behandlingshendelsen inntreffer
_før_ `T`.

```
Tidslinje (bekreftet av produksjonslogs, eks. median-tilfelle):
  T-20s  BehandlingsflytHendelse: UNDER_BEHANDLING (→ ManglerEnhet)
  T-0.5s BehandlingsflytHendelse: AVSLUTTET
  T      OppgaveLukketHendelse  (sendtTid = T)

snapshots for UNDER_BEHANDLING-tilstand:  siste tidspunkt = T  → endretTid = T
snapshots for AVSLUTTET-tilstand:         siste tidspunkt = T  → endretTid = T  ← kollisjon
```

---

## Feilkilde 4 – `hentHendelseMedEndretTid` filtrerer på `er_relast`

SQL-spørringen som sjekker om en eldre hendelse allerede er lagret (idempotenssjekk):

```sql
SELECT * FROM saksstatistikk
WHERE behandling_uuid = ? AND endret_tid = ? AND er_relast = ?
```

Filteret `er_relast = ?` betyr at en vanlig hendelse (`erResending = false`) og en
resend-hendelse (`erResending = true`) ved samme `endretTid` **ikke** oppdager hverandre.
Kan gi duplikat-rader ved overlappende resend og regulær jobb.

### Produksjonsstatus

`ResendSakstatistikkJobb` har **ikke kjørt en eneste gang** de siste 30 dagene i prod.
Alle Loki-søk på `resend`, `er_relast`, `erResending` og jobbtypen
`statistikk.resendSakstatistikk` returnerte 0 treff. Buggen er **latent** — den vil
manifestere seg første gang jobben trigges manuelt (f.eks. via admin-API). Ingen
konsekvenser per nå.

---

## Feilkilde 5 – `siste` er foreldet etter inngangs-hendelse-opprettelse

I `lagreBQBehandling()`:

```kotlin
val siste = sakstatistikkRepository.hentSisteHendelseForBehandling(bqSak.behandlingUUID)
// ...
if (!erInngangsHendelse(bqSak) && siste == null) {
    sakstatistikkRepository.lagre(bqSak.copy(..., behandlingStatus = "OPPRETTET"))
    // siste er fortsatt null etter denne linjen
}
val bqSakMedUnikEndretTid = tilpassEndretTid(bqSak, siste)  // ← bruker gammel siste (null)
```

Svært lite sannsynlig i praksis, men `siste` er stale etter at inngangs-hendelsen er lagret.

### Produksjonsstatus

Ingen logg-evidens — og av en konkret grunn: koden har **ingen log-statements** rundt dette
kodestykket. Verken `siste`-verdien, `erInngangsHendelse`-flagget, eller om
`tilpassEndretTid()` ble kalt med `null` logges. En feil her ville vært **helt usynlig** i
logger selv om den inntraff. Feilkilde 5 er dermed **ikke observerbar** uten å legge til
instrumentering.

---

## Manuell datagjenoppretting

### BigQuery — identifisere feilordnede rader

Finner behandlinger der siste rad (høyeste `endretTid`) **ikke** er AVSLUTTET, men det
eksisterer en AVSLUTTET-rad med lavere `endretTid`:

```sql
WITH gjeldende AS (
  SELECT behandling_uuid, endretTid, behandling_status, sekvensnummer, teknisk_tid
  FROM `saksstatistikk.view_gjeldende_hendelser_saksstatistikk`
),
avsluttet AS (
  SELECT behandling_uuid, endretTid AS avsluttet_endret_tid
  FROM gjeldende
  WHERE behandling_status = 'AVSLUTTET'
)
SELECT
  g.behandling_uuid,
  g.behandling_status  AS feil_status,
  g.endretTid          AS feil_endret_tid,
  a.avsluttet_endret_tid,
  g.sekvensnummer,
  g.teknisk_tid
FROM gjeldende g
JOIN avsluttet a ON g.behandling_uuid = a.behandling_uuid
WHERE g.endretTid > a.avsluttet_endret_tid
ORDER BY g.behandling_uuid, g.endretTid
```

### Postgres — slette feilrader

`sekvensnummer` i BQ-resultatet er `id`-kolonnen i `saksstatistikk`-tabellen. Slett
identifiserte rader og Datastream synkroniserer slettingen til BigQuery automatisk:

```sql
DELETE FROM saksstatistikk WHERE id = <sekvensnummer>;
```

---

## Oppsummering av feilkilder

| #   | Feilkilde                                                                                   | Alvorlighet                  | Produksjonsstatus                                   |
| --- | ------------------------------------------------------------------------------------------- | ---------------------------- | --------------------------------------------------- |
| 1   | ManglerEnhet-retry med frossen snapshot → bump forbi AVSLUTTET                              | Høy – feil rekkefølge i BQ   | **Bekreftet — ~10 tilfeller/dag, ~300/30d**         |
| 2   | Bumplogikk uten statusvakt + feilsjekk dekker kun IVERKSETTES (ikke UNDER_BEHANDLING)       | Høy – lagrer feil rekkefølge | **Bekreftet — feilsjekk aldri utløst (0 hits)**     |
| 3   | UNDER_BEHANDLING og AVSLUTTET beregner samme `endretTid` pga. oppgave-LUKKET                | Medium – trigger for bump    | **Bekreftet — identisk endretTid i alle saker**     |
| 4   | `hentHendelseMedEndretTid` filtrerer på `er_relast` → resend/vanlig oppdager ikke hverandre | Lav–Medium – duplikat-rader  | **Latent — ResendSakstatistikkJobb aldri kjørt**    |
| 5   | `siste` er foreldet etter inngangs-hendelse-opprettelse                                     | Lav                          | **Ikke observerbar — mangler logg-instrumentering** |

---

## Relevante kodefiler

| Fil                              | Relevante linjer                                                     |
| -------------------------------- | -------------------------------------------------------------------- |
| `SaksStatistikkService.kt`       | `lagreBQBehandling()` (l. 91–130), `tilpassEndretTid()` (l. 139–174) |
| `BQBehandlingMapper.kt`          | `bqBehandlingForBehandling()` – beregning av `endretTid` (l. 105)    |
| `SakstatistikkEventSourcing.kt`  | `byggSakstatistikkHendelser()` – sortert hendelsesrekkefølge         |
| `LagreSakinfoTilBigQueryJobb.kt` | `utførJobb()` – ManglerEnhet-retry med `storedBQBehandling`          |
| `SakstatistikkRepositoryImpl.kt` | `hentSisteHendelseForBehandling()`, `hentHendelseMedEndretTid()`     |
| `BQSak.kt`                       | `ansesSomDuplikat()` – hvilke felt ignoreres i duplikatsjekken       |
