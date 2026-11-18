---
name: rett-hendelseshistorikk
description: Rett historikk, saksbehandler, enhet, hendelsesrekkefølge eller duplikater i saksstatistikken. Bruk ved forsinkede hendelser, feil gjeldende-rad, cutoff, retry, resending eller avklaringsbehov som gir feil historisk tilstand.
---

# Rett hendelseshistorikk

Reelle eksempler: `37757e2e` (cutoff gjennom retry), `2b5f750c` (fjern frosset snapshot),
`52cfe1cd` og `412efcc2` (gjeldende-rad), `5328a61b` (frivillige avklaringsbehov).

## Finn tidslinjen før du endrer kode

1. Skriv ned forventede hendelser med funksjonelt tidspunkt, mottakstid, status, avklaringsbehov,
   saksbehandler og enhet. Skill hendelsestid fra teknisk prosesseringstid.
2. Finn laget som introduserer avviket:
    - Kontrakt til historikk: [hendelseHjelpere.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/hendelser/hendelseHjelpere.kt#L1),
      [BehandlingService.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/hendelser/BehandlingService.kt#L1)
      og [BehandlingRepository.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/behandling/BehandlingRepository.kt#L1).
    - Samspill med oppgaver: [SakstatistikkEventSourcing.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/SakstatistikkEventSourcing.kt#L1)
      og [BQBehandlingMapper.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/BQBehandlingMapper.kt#L1).
    - Deduplisering og lagring: [SaksStatistikkService.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/SaksStatistikkService.kt#L1).
3. Lag en liten regresjonstest før rettingen. Velg eksisterende `HendelseHjelpereKtTest`,
   `BehandlingRepositoryTest`, `SakstatistikkEventSourcingTest`, `BQBehandlingMapperTest`
   eller `SaksStatistikkServiceTest` etter hvilket lag som feiler.

## Bevar disse egenskapene

- Historiske rader skal ha saksbehandler og enhet som gjaldt ved hendelsen. Informasjon som kommer senere,
  kan oppklare historikken, men skal ikke gjøre dagens reservasjon til historisk fasit.
- Retry skal lese tilgjengelige data på nytt, men beholde det opprinnelige `cutoffTidspunkt`.
  Se [LagreSakinfoTilBigQueryJobb.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/LagreSakinfoTilBigQueryJobb.kt#L23).
  Ikke legg et ferdig beregnet `BQBehandling`-snapshot i payloaden.
- En eldre hendelse skal ikke bli gjeldende bare fordi den ble mottatt sist.
  Bevar repository-ets låsing og deterministiske valg av gjeldende-rad.
- [BQBehandling.ansesSomDuplikat](../../../app/src/main/kotlin/no/nav/aap/statistikk/saksstatistikk/BQSak.kt#L63)
  ignorerer `sekvensNummer`, `erResending`, `tekniskTid`, `endretTid` og `versjon`.
  Endret saksbehandler eller enhet er derimot en reell endring.
  Ikke tving frem nye rader ved bare å endre et ignorert felt.
- Bevar skjerming, rollebytter og skillet mellom automatisk og manuell behandling.
  Ikke skjul manglende enhet med en vilkårlig standardverdi eller ubegrenset retry.

Test de tilfellene rettingen berører: omvendt ankomstrekkefølge, likt hendelsestidspunkt,
gjentatt levering, oppgavedata som kommer senere, og hendelser etter avslutning.
Bruk faste tidspunkter fremfor venting.

## Resending er en egen flyt

Hvis rettingen endrer utledning av historikk, følg også
[ResendHendelseService.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/hendelser/ResendHendelseService.kt#L40)
og [ReberegnHistorikk.kt](../../../app/src/main/kotlin/no/nav/aap/statistikk/hendelser/ReberegnHistorikk.kt#L1).
Utvid `ResendHendelseServiceTest` når den samme regelen skal gjelde ved rekonstruksjon.

En kodefiks reparerer ikke automatisk lagrede data. Beskriv om eksisterende rader trenger
rekjøring, men ikke start resending eller kjør reparasjons-SQL mot et miljø uten eksplisitt bestilling.
Avslutt med `verifiser`.
