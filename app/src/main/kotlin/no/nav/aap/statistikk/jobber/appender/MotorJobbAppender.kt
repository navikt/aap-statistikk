package no.nav.aap.statistikk.jobber.appender

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.api.stringToNumber
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.bigquery.LagreSakinfoTilBigQueryJobb

class MotorJobbAppender(
    private val lagreSakinfoTilBigQueryJobb: LagreSakinfoTilBigQueryJobb,
    private val lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb,
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
        val saksnummer = BehandlingRepository(connection).hent(behandlingId).sak.saksnummer
        leggTil(
            connection,
            // For sak = behandlingId. Husk at "sak" er funksjonalt bare en concurrency-key
            JobbInput(lagreSakinfoTilBigQueryJobb).medPayload(behandlingId)
                .forSak(stringToNumber(saksnummer.value))
        )
    }

    override fun leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
        connection: DBConnection,
        behandlingId: BehandlingId
    ) {
        val behandling = BehandlingRepository(connection).hent(behandlingId)
        val saksnummer = behandling.sak.saksnummer
        leggTil(
            connection, JobbInput(lagreAvsluttetBehandlingTilBigQueryJobb).medPayload(
                behandling.referanse
            ).forSak(
                stringToNumber(saksnummer.value)
            )
        )
    }
}