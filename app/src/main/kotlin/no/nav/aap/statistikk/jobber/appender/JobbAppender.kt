package no.nav.aap.statistikk.jobber.appender

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.behandling.BehandlingId

interface JobbAppender {
    fun leggTil(connection: DBConnection, jobb: JobbInput)
    fun leggTilLagreSakTilBigQueryJobb(
        connection: DBConnection, behandlingId: BehandlingId, delayInMillis: Long = 0
    )

    fun leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
        connection: DBConnection, behandlingId: BehandlingId
    )

    fun leggTilResendSakstatistikkJobb(
        connection: DBConnection, behandlingId: BehandlingId
    )
}