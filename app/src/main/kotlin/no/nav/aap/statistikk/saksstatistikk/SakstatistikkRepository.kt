package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.repository.Repository
import java.util.*

interface SakstatistikkRepository : Repository {
    fun lagre(bqBehandling: BQBehandling): Long
    fun lagreFlere(bqBehandlinger: List<BQBehandling>)

    fun hentSisteHendelseForBehandling(uuid: UUID): BQBehandling?

    fun hentAlleHendelserPåBehandling(referanse: UUID): List<BQBehandling>
}

