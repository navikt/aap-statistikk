package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.statistikk.behandling.BehandlingId

interface SamordningRepository : Repository {
    fun lagre(behandlingId: BehandlingId, samordning: Samordning)
    fun hent(behandlingId: BehandlingId): Samordning?
}
