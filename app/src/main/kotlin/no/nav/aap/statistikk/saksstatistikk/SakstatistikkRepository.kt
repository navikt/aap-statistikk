package no.nav.aap.statistikk.saksstatistikk

import org.jetbrains.annotations.TestOnly

interface SakstatistikkRepository {
    fun lagre(bqBehandling: BQBehandling): Long
    fun lagreFlere(bqBehandlinger: List<BQBehandling>): List<Long>

    @TestOnly
    fun hentSisteForBehandling(id: Long): BQBehandling
}

