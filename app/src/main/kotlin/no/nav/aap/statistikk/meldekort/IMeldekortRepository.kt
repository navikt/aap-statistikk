package no.nav.aap.statistikk.meldekort

import no.nav.aap.statistikk.behandling.BehandlingId

interface IMeldekortRepository {
    fun lagre(behandlingId: BehandlingId, meldekort: List<Meldekort>)
    fun hentMeldekortperioder(behandlingId: BehandlingId): List<Meldekort>
}