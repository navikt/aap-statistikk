package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.behandling.BQYtelseBehandling


interface IBQYtelsesstatistikkRepository {
    fun lagre(payload: BQYtelseBehandling)
    fun start()
    fun commit()
}