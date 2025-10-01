package no.nav.aap.statistikk.saksstatistikk

import java.util.*

interface SakstatistikkRepository {
    fun lagre(bqBehandling: BQBehandling): Long
    fun lagreFlere(bqBehandlinger: List<BQBehandling>)

    fun hentSisteHendelseForBehandling(uuid: UUID): BQBehandling?

    fun hentAlleHendelserPåBehandling(referanse: UUID): List<BQBehandling>
}

