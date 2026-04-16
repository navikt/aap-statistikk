# Risikovurdering: aap-statistikk

> Generert av nav-deep-interview · 2026-04-13

## Arketype

Backend API + Hendelsekonsument (Kafka/HTTP) — eksisterende brownfield-tjeneste

---

## 🔴 Kritiske funn

### 1. Manglende retensjonsstrategi

**Risiko:** GDPR-brudd

Tjenesten lagrer helseopplysninger (Art. 9 GDPR) og fødselsnummer i PostgreSQL uten dokumenterte
sletteregler. Data kan vokse ubegrenset.

**Tiltak:** Definer og implementer retensjonspolicy. Dokumenter i behandlingskatalogen. Vurder om
BigQuery-datasett også trenger sletteregler.

---

### 2. `allowAllUsers: true` med helseopplysninger

**Risiko:** Uautorisert tilgang

Azure AD er konfigurert med `allowAllUsers: true`, som betyr at _alle_ NAV-ansatte kan få token for
tjenesten. For en tjeneste med strengt fortrolig data (helseopplysninger) er dette for åpent.

**Tiltak:** Begrens til spesifikke AD-grupper eller tjenester. Vurder om saksbehandler-tilgangen
(kodeverk-siden) kan skilles ut.

---

### 3. Ingen audit-logging

**Risiko:** GDPR-krav ikke oppfylt

Helseopplysninger krever audit-logging av hvem som har lest/endret data. Dette er ikke implementert.

**Tiltak:** Implementer audit-logging for tilgang til sensitive data — minst `NAVident` + tidspunkt +
hvilken ressurs.

---

### 4. Ingen varsling (alerts)

**Risiko:** Feil oppdages ikke før brukere klager

Ingen alerts er satt opp, til tross for at tjenesten leverer produksjonsstyringsdata som
saksbehandlere er avhengige av.

**Tiltak:** Sett opp minimum: alert på feilrate, alert på lag i hendelsesprosessering, alert på
PDL-feil.

---

## 🟡 Middels risiko

### 5. HikariCP pool-størrelse: 16 connections

`hikariMaxPoolSize = ktorParallellitet(8) + 2×ANTALL_WORKERS(4) = 16`

Med en `db-custom-1-3840`-instans (1 vCPU) og potensielt flere replicas gir dette 16+ connections
per pod. PostgreSQL anbefaler `2×CPU + 1 = 3` for 1 vCPU. Risiko for pool exhaustion og
databasetrøbbel.

**Tiltak:** Reduser pool til 3–5, og verifiser `antall replicas × pool` mot databasens
`max_connections`.

---

### 6. Manuell dead-letter-håndtering

Hendelser som feiler håndteres manuelt. Dette skalerer ikke og medfører risiko for at poison pills
stopper prosessering uten at noen varsles.

**Tiltak:** Kombiner med punkt 4 — alert på feilende hendelser + dokumenter prosedyre for manuell
håndtering.

---

### 7. Datakvalitet-metrikker kun i logger

Duplikater og rekkefølgefeil logges, men er ikke eksponert som Prometheus-metrikker. Kan ikke
visualiseres i Grafana eller trigge alerts.

**Tiltak:** Konverter til Prometheus-metrikker (`Counter`/`Gauge`) slik at de kan overvåkes og
varsles på.

---

## 🟢 Det som fungerer bra

- ✅ `accessPolicy.inbound` er korrekt konfigurert med eksplisitte regler
- ✅ Idempotens er implementert (duplikat-håndtering i BQBehandling)
- ✅ Schema-evolusjon håndteres med bakoverkompatibilitet
- ✅ Parameteriserte SQL-queries
- ✅ OpenTelemetry auto-instrumentering aktivert
- ✅ Retry på PDL-kall
- ✅ Hjemmel dokumentert i behandlingskatalogen
- ✅ Ingen PII i logger (verifisert i kode)

---

## Prioriterte neste steg

| Prioritet | Tiltak                                              | Område         |
| --------- | --------------------------------------------------- | -------------- |
| 1         | Definer retensjonspolicy (PostgreSQL + BigQuery)    | Personvern     |
| 2         | Endre `allowAllUsers: true` til AD-gruppe           | Auth           |
| 3         | Implementer audit-logging for helseopplysninger     | Personvern     |
| 4         | Sett opp minimum alerting (feilrate, hendelsesslag) | Observabilitet |
| 5         | Juster HikariCP pool-størrelse ned til 3–5          | Database       |
| 6         | Eksponér datakvalitet som Prometheus-metrikker      | Observabilitet |
