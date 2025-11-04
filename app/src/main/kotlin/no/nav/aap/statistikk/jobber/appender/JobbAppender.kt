package no.nav.aap.statistikk.jobber.appender

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.behandling.BehandlingId

interface JobbAppender {
    fun leggTil(connection: DBConnection, jobb: JobbInput)
    fun leggTil(repositoryProvider: RepositoryProvider, jobb: JobbInput)
    fun leggTilLagreSakTilBigQueryJobb(
        repositoryProvider: RepositoryProvider, behandlingId: BehandlingId, delayInSeconds: Long = 0
    )

    fun leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
        connection: DBConnection, behandlingId: BehandlingId
    )

    fun leggTilResendSakstatistikkJobb(
        repositoryProvider: RepositoryProvider, behandlingId: BehandlingId
    )
}