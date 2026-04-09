package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.behandling.BQYtelseBehandling

@Deprecated("Vil bli fjernet såsnart Team Spenn går over til å bruke view_behandlinger.")
interface BQYtelsesstatistikkRepository {
    fun lagre(payload: BQYtelseBehandling)
    fun start()
    fun commit()
}