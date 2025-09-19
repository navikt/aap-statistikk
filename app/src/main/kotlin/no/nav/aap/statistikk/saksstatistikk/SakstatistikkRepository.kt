package no.nav.aap.statistikk.saksstatistikk

import org.jetbrains.annotations.TestOnly

interface SakstatistikkRepository {
    fun lagre(bqBehandling: BQBehandling): Long

    @TestOnly
    fun hentSisteForBehandling(id: Long): BQBehandling
}

