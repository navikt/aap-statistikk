package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.repository.Repository
import java.util.*

interface IBehandlingRepository : Repository {
    fun opprettBehandling(behandling: Behandling): BehandlingId

    fun oppdaterBehandling(behandling: Behandling)

    fun invaliderOgLagreNyHistorikk(behandling: Behandling)

    /**
     * Henter gjeldende behandling med alle aktive historikkhendelser.
     * Returnerer null hvis behandlingen ikke finnes.
     */
    fun hentMedHistorikk(referanse: UUID): Behandling?

    /**
     * Henter gjeldende behandling uten å laste historikken.
     * Tom hendelsesliste betyr her at historikken ikke er lastet, ikke at den mangler.
     * Returnerer null hvis behandlingen ikke finnes.
     */
    fun hentUtenHistorikkEllerNull(id: BehandlingId): Behandling?

    /**
     * Henter gjeldende behandling med alle aktive historikkhendelser.
     * Feiler hvis behandlingen ikke finnes.
     */
    fun hentMedHistorikk(id: BehandlingId): Behandling

    /**
     * Henter gjeldende behandling med alle aktive historikkhendelser og pessimistisk lås (FOR UPDATE).
     * Blokkerer andre transaksjoner fra å endre raden før transaksjonen er ferdig.
     * Feiler hvis behandlingen ikke finnes.
     */
    fun hentMedHistorikkForUpdate(id: BehandlingId): Behandling

    /**
     * Henter gjeldende behandling på referanse med alle aktive historikkhendelser og pessimistisk lås (FOR UPDATE).
     * Returnerer null hvis behandling ikke finnes.
     */
    fun hentMedHistorikkForUpdate(referanse: UUID): Behandling?
}
