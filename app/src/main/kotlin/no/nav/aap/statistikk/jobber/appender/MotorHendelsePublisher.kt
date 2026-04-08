package no.nav.aap.statistikk.jobber.appender

import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import org.slf4j.LoggerFactory

class MotorHendelsePublisher(
    private val jobbAppender: JobbAppender,
    private val repositoryProvider: RepositoryProvider,
    private val lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb? = null,
) : HendelsePublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun publiser(hendelse: StatistikkHendelse) {
        when (hendelse) {
            is StatistikkHendelse.SakstatistikkSkalLagres -> {
                log.info("Legger til lagretilsaksstatistikkjobb. BehandlingId: ${hendelse.behandlingId}")
                jobbAppender.leggTilLagreSakTilBigQueryJobb(
                    repositoryProvider,
                    hendelse.behandlingId,
                    // Veldig hacky! Dette er for at jobben som kjører etter melding fra
                    // oppgave-appen skal få tid til å oppdatere enhet-tabellen før denne kjører.
                    delayInSeconds = System.getenv("HACKY_DELAY")?.toLong() ?: 0L,
                    triggerKilde = "behandling"
                )
            }

            is StatistikkHendelse.YtelsesstatistikkSkalLagres -> {
                val jobb = checkNotNull(lagreAvsluttetBehandlingTilBigQueryJobb) {
                    "lagreAvsluttetBehandlingTilBigQueryJobb må være satt for å publisere YtelsesstatistikkSkalLagres"
                }
                jobbAppender.leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
                    repositoryProvider,
                    hendelse.behandlingId,
                    jobb
                )
            }

            is StatistikkHendelse.SakstatistikkSkalResendes -> {
                log.info("Starter resending-jobb. BehandlingId: ${hendelse.behandlingId}")
                jobbAppender.leggTilResendSakstatistikkJobb(repositoryProvider, hendelse.behandlingId)
            }
        }
    }
}
