package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.komponenter.type.Periode
import no.nav.aap.statistikk.behandling.BehandlingId

interface ArbeidsopptrappingperioderRepository : Repository {
    fun lagre(behandlingId: BehandlingId, perioder: List<Periode>)
    fun hent(behandlingId: BehandlingId): List<Periode>?
}