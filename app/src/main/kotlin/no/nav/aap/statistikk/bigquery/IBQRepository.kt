package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.behandling.BQYtelseBehandling
import no.nav.aap.statistikk.saksstatistikk.BQBehandling

@Deprecated("Vil erstattes av lagring+replikering.")
interface IBQSakstatistikkRepository {
    fun lagre(payload: BQBehandling)
}

interface IBQYtelsesstatistikkRepository {
    fun lagre(payload: BQYtelseBehandling)
    fun start()
    fun commit()
}