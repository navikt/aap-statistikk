package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.repository.Repository
import java.util.*

interface IBehandlingRepository : Repository {
    fun opprettBehandling(behandling: Behandling): BehandlingId

    fun oppdaterBehandling(behandling: Behandling)

    fun invaliderOgLagreNyHistorikk(behandling: Behandling)

    fun hent(referanse: UUID): Behandling?

    fun hentEllerNull(id: BehandlingId): Behandling?

    fun hent(id: BehandlingId): Behandling
    
    /**
     * Fetch behandling with pessimistic lock (FOR UPDATE).
     * Blocks other transactions from modifying this row until transaction completes.
     */
    fun hentBehandlingForUpdate(id: BehandlingId): Behandling
    
    /**
     * Fetch behandling by referanse with pessimistic lock (FOR UPDATE).
     * Returns null if not found.
     */
    fun hentBehandlingForUpdate(referanse: UUID): Behandling?
}