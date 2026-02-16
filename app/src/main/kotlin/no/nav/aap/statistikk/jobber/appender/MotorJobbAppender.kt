package no.nav.aap.statistikk.jobber.appender

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.api.stringToNumber
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.postgresRepositoryRegistry
import no.nav.aap.statistikk.saksstatistikk.Konstanter
import no.nav.aap.statistikk.saksstatistikk.LagreSakinfoTilBigQueryJobb
import no.nav.aap.statistikk.saksstatistikk.ResendSakstatistikkJobb
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class MotorJobbAppender : JobbAppender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun leggTil(
        connection: DBConnection,
        jobb: JobbInput
    ) {
        leggTil(postgresRepositoryRegistry.provider(connection), jobb)
    }

    override fun leggTil(
        repositoryProvider: RepositoryProvider,
        jobb: JobbInput
    ) {
        repositoryProvider.provide<FlytJobbRepository>().leggTil(jobb)
    }

    override fun leggTilLagreSakTilBigQueryJobb(
        repositoryProvider: RepositoryProvider,
        behandlingId: BehandlingId,
        delayInSeconds: Long,
        enhetRetryCount: Int,
        originalHendelsestid: LocalDateTime?
    ) {
        val behandling =
            repositoryProvider.provide<IBehandlingRepository>()
                .hent(behandlingId)
        if (behandling.typeBehandling !in Konstanter.interessanteBehandlingstyper) {
            log.info("Prøver å legge til uinteressant behandling til saksstatikk. Ignorerer. Behandling: $behandlingId. Referanse: ${behandling.referanse}")
            return
        }

        val saksnummer = behandling.sak.saksnummer
        val jobbInput = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(behandlingId)
            .medNesteKjøring(LocalDateTime.now().plusSeconds(delayInSeconds))
            .forSak(stringToNumber(saksnummer.value))

        if (enhetRetryCount > 0) {
            jobbInput.medParameter("enhetRetryCount", enhetRetryCount.toString())
        }

        if (originalHendelsestid != null) {
            jobbInput.medParameter("originalHendelsestid", originalHendelsestid.toString())
        }

        leggTil(repositoryProvider, jobbInput)
    }

    override fun leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
        provider: RepositoryProvider,
        behandlingId: BehandlingId,
        lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb
    ) {
        val behandling =
            provider.provide<IBehandlingRepository>()
                .hent(behandlingId)
        val saksnummer = behandling.sak.saksnummer
        leggTil(
            provider, JobbInput(lagreAvsluttetBehandlingTilBigQueryJobb).medPayload(
                behandling.referanse
            ).forSak(
                stringToNumber(saksnummer.value)
            )
        )
    }

    override fun leggTilResendSakstatistikkJobb(
        repositoryProvider: RepositoryProvider,
        behandlingId: BehandlingId
    ) {
        log.info("Starter resending-jobb. BehandlingId: $behandlingId")
        val behandling =
            repositoryProvider.provide<IBehandlingRepository>()
                .hent(behandlingId)
        val saksnummer = behandling.sak.saksnummer
        leggTil(
            repositoryProvider,
            JobbInput(ResendSakstatistikkJobb()).medPayload(behandlingId)
                .forSak(stringToNumber(saksnummer.value))
        )
    }
}
// rad endret og og behandling avsluttet-tid mangler i view
// hvis fritak fra meldekort-status, dele tidslinjen