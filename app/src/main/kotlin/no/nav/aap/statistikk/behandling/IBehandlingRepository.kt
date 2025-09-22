package no.nav.aap.statistikk.behandling

import java.util.*

interface IBehandlingRepository {
    fun opprettBehandling(behandling: Behandling): BehandlingId

    fun oppdaterBehandling(behandling: Behandling)

    fun invaliderOgLagreNyHistorikk(behandling: Behandling)

    fun hent(referanse: UUID): Behandling?

    fun hentEllerNull(id: BehandlingId): Behandling?

    fun hent(id: BehandlingId): Behandling

    fun tellFullførteBehandlinger(): Long
}