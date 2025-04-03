package no.nav.aap.statistikk.jobber.appender

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.bigquery.LagreSakinfoTilBigQueryJobb

class MotorJobbAppender(
    private val lagreSakinfoTilBigQueryJobb: LagreSakinfoTilBigQueryJobb,
) : JobbAppender {
    override fun leggTil(
        connection: DBConnection,
        jobb: JobbInput
    ) {
        FlytJobbRepository(connection).leggTil(jobb)
    }

    override fun leggTilLagreSakTilBigQueryJobb(
        connection: DBConnection,
        behandlingId: BehandlingId
    ) {
        leggTil(
            connection,
            // For sak = behandlingId. Husk at "sak" er funksjonalt bare en concurrency-key
            JobbInput(lagreSakinfoTilBigQueryJobb).medPayload(behandlingId).forSak(behandlingId)
        )
    }
}