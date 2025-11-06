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
}