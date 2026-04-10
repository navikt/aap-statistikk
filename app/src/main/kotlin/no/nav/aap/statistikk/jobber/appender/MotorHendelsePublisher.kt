package no.nav.aap.statistikk.jobber.appender

import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import org.slf4j.LoggerFactory

class MotorHendelsePublisher private constructor(
    private val jobbAppender: JobbAppender,
    private val repositoryProvider: RepositoryProvider,
    private val lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb?,
) : HendelsePublisher {

    companion object {
        fun medYtelseJobb(
            jobbAppender: JobbAppender,
            repositoryProvider: RepositoryProvider,
            lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb,
        ) = MotorHendelsePublisher(jobbAppender, repositoryProvider, lagreAvsluttetBehandlingTilBigQueryJobb)

        fun utenYtelseJobb(
            jobbAppender: JobbAppender,
            repositoryProvider: RepositoryProvider,
        ) = MotorHendelsePublisher(jobbAppender, repositoryProvider, null)
    }

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
                val jobb = lagreAvsluttetBehandlingTilBigQueryJobb
                    ?: throw UnsupportedOperationException(
                        "Denne publisher-instansen støtter ikke YtelsesstatistikkSkalLagres. Bruk medYtelseJobb() ved konstruksjon."
                    )
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
