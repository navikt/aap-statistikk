package no.nav.aap.statistikk.saksstatistikk

import org.jetbrains.annotations.TestOnly
import java.util.UUID

interface SakstatistikkRepository {
    fun lagre(bqBehandling: BQBehandling): Long
    fun lagreFlere(bqBehandlinger: List<BQBehandling>)

    @TestOnly
    fun hentSisteForBehandling(referanse: UUID): BQBehandling
}

