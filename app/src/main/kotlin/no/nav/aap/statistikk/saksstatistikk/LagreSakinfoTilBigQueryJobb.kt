package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProvidersJobbSpesifikasjon
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

data class EnhetRetryConfig(
    val maxRetries: Int = requiredConfigForKey("enhet.retry.max.retries").toInt(),
    val delaySeconds: Long = requiredConfigForKey("enhet.retry.delay.seconds").toLong()
)

class LagreSakinfoTilBigQueryJobbUtfører(
    private val sakStatistikkService: ISaksStatistikkService,
    private val jobbAppender: JobbAppender,
    private val repositoryProvider: RepositoryProvider,
    private val enhetRetryConfig: EnhetRetryConfig = EnhetRetryConfig()
) :
    JobbUtfører {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val behandlingId = input.payload<BehandlingId>()
        val retryCount = input.optionalParameter("enhetRetryCount")?.toInt() ?: 0
        val originalHendelsestid = input.optionalParameter("originalHendelsestid")
            ?.let { LocalDateTime.parse(it) }

        val resultat = if (originalHendelsestid != null) {
            sakStatistikkService.lagreMedOppgavedata(behandlingId, originalHendelsestid)
        } else {
            sakStatistikkService.lagreSakInfoTilBigquery(behandlingId)
        }

        when (resultat) {
            SakStatistikkResultat.OK -> {}
            is SakStatistikkResultat.ManglerEnhet -> {
                if (retryCount < enhetRetryConfig.maxRetries) {
                    log.info(
                        "Enhet mangler for behandling ${resultat.behandlingId}, " +
                                "avklaringsbehov=${resultat.avklaringsbehovKode}. " +
                                "Reschedulerer om ${enhetRetryConfig.delaySeconds}s " +
                                "(forsøk ${retryCount + 1}/${enhetRetryConfig.maxRetries})."
                    )
                    jobbAppender.leggTilLagreSakTilBigQueryJobb(
                        repositoryProvider,
                        behandlingId,
                        delayInSeconds = enhetRetryConfig.delaySeconds,
                        enhetRetryCount = retryCount + 1,
                        originalHendelsestid = resultat.hendelsestid
                    )
                } else {
                    log.warn(
                        "Enhet mangler fortsatt etter ${enhetRetryConfig.maxRetries} forsøk " +
                                "for behandling ${resultat.behandlingId}, " +
                                "avklaringsbehov=${resultat.avklaringsbehovKode}. " +
                                "Lagrer med null enhet."
                    )
                    if (originalHendelsestid != null) {
                        sakStatistikkService.lagreMedOppgavedata(
                            behandlingId, originalHendelsestid, lagreUtenEnhet = true
                        )
                    } else {
                        sakStatistikkService.lagreSakInfoTilBigquery(
                            behandlingId, lagreUtenEnhet = true
                        )
                    }
                }
            }
        }
    }
}

class LagreSakinfoTilBigQueryJobb : ProvidersJobbSpesifikasjon {
    override fun konstruer(
        repositoryProvider: RepositoryProvider,
        gatewayProvider: GatewayProvider
    ): JobbUtfører {
        val sakStatistikkService = SaksStatistikkService.konstruer(
            gatewayProvider,
            repositoryProvider,
        )
        return LagreSakinfoTilBigQueryJobbUtfører(
            sakStatistikkService,
            MotorJobbAppender(),
            repositoryProvider
        )
    }

    override val retries = 1
    override val type: String = "statistikk.lagreSakinfoTilBigQueryJobb"
    override val navn: String = "lagreSakinfoTilBigQuery"

    override val beskrivelse: String = "Lagrer sakinfo til BigQuery"
}
