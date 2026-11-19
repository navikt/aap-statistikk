package no.nav.aap.statistikk.jobber.appender

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.saksstatistikk.LagreSakinfoPayload

interface JobbAppender {
    fun leggTil(connection: DBConnection, jobb: JobbInput)
    fun leggTil(repositoryProvider: RepositoryProvider, jobb: JobbInput)
    fun leggTilLagreSakTilBigQueryJobb(
        repositoryProvider: RepositoryProvider,
        payload: LagreSakinfoPayload,
        delayInSeconds: Long = 0,
    )

    fun leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
        provider: RepositoryProvider,
        behandlingId: BehandlingId,
        lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb,
    )

    fun leggTilResendSakstatistikkJobb(repositoryProvider: RepositoryProvider, behandlingId: BehandlingId)
}
