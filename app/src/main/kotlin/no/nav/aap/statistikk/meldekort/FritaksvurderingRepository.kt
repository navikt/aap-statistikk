package no.nav.aap.statistikk.meldekort

import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.statistikk.behandling.BehandlingId

interface FritaksvurderingRepository : Repository {
    fun lagre(behandlingId: BehandlingId, vurderinger: List<Fritakvurdering>)
    fun hentFritaksvurderinger(behandlingId: BehandlingId): List<Fritakvurdering>
}