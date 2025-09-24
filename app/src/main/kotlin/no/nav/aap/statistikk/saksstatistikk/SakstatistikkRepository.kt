package no.nav.aap.statistikk.saksstatistikk

import org.jetbrains.annotations.TestOnly
import java.util.*

interface SakstatistikkRepository {
    fun lagre(bqBehandling: BQBehandling): Long
    fun lagreFlere(bqBehandlinger: List<BQBehandling>)

    @TestOnly
    fun hentSisteForBehandling(referanse: UUID): List<BQBehandling>
}

