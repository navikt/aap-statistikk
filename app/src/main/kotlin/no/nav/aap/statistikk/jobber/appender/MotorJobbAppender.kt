package no.nav.aap.statistikk.jobber.appender

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.api.stringToNumber
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.postgresRepositoryRegistry
import no.nav.aap.statistikk.saksstatistikk.LagreSakinfoTilBigQueryJobb
import no.nav.aap.statistikk.saksstatistikk.ResendSakstatistikkJobb
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class MotorJobbAppender(
    private val lagreSakinfoTilBigQueryJobb: LagreSakinfoTilBigQueryJobb,
    private val lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb,
    private val resendSakstatistikkJobb: ResendSakstatistikkJobb,
) : JobbAppender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun leggTil(
        connection: DBConnection,
        jobb: JobbInput
    ) {
        postgresRepositoryRegistry.provider(connection).provide<FlytJobbRepository>().leggTil(jobb)
    }

    override fun leggTilLagreSakTilBigQueryJobb(
        connection: DBConnection,
        behandlingId: BehandlingId,
        delayInMillis: Long
    ) {
        val behandling =
            postgresRepositoryRegistry.provider(connection).provide<IBehandlingRepository>()
                .hent(behandlingId)
        if (behandling.typeBehandling in listOf(TypeBehandling.Oppfølgingsbehandling)) {
            log.info("Prøver å legge til oppfølgingsbehandling til saksstatikk. Ignorerer. Behandling: $behandlingId. Referanse: ${behandling.referanse}")
            return
        }

        val saksnummer = behandling.sak.saksnummer
        leggTil(
            connection,
            // For sak = behandlingId. Husk at "sak" er funksjonalt bare en concurrency-key
            JobbInput(lagreSakinfoTilBigQueryJobb)
                .medPayload(behandlingId)
                .medNesteKjøring(LocalDateTime.now().plusNanos(delayInMillis * 1000))
                .forSak(stringToNumber(saksnummer.value))
        )
    }

    override fun leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
        connection: DBConnection,
        behandlingId: BehandlingId
    ) {
        val behandling =
            postgresRepositoryRegistry.provider(connection).provide<IBehandlingRepository>()
                .hent(behandlingId)
        val saksnummer = behandling.sak.saksnummer
        leggTil(
            connection, JobbInput(lagreAvsluttetBehandlingTilBigQueryJobb).medPayload(
                behandling.referanse
            ).forSak(
                stringToNumber(saksnummer.value)
            )
        )
    }

    override fun leggTilResendSakstatistikkJobb(
        connection: DBConnection,
        behandlingId: BehandlingId
    ) {
        log.info("Starter resending-jobb. BehandlingId: $behandlingId")
        val behandling =
            postgresRepositoryRegistry.provider(connection).provide<IBehandlingRepository>()
                .hent(behandlingId)
        val saksnummer = behandling.sak.saksnummer
        leggTil(
            connection,
            JobbInput(resendSakstatistikkJobb).medPayload(behandlingId)
                .forSak(stringToNumber(saksnummer.value))
        )
    }
}