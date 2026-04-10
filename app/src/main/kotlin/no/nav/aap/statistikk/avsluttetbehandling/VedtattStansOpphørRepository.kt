package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.statistikk.behandling.BehandlingId

interface VedtattStansOpphørRepository : Repository {
    fun lagre(behandlingId: BehandlingId, vedtattStansOpphør: List<StansEllerOpphør>)
}
