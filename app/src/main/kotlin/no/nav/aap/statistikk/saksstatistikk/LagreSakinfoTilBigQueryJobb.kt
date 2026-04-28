package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.komponenter.json.DeserializationException
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProvidersJobbSpesifikasjon
import no.nav.aap.statistikk.LoggingKontekst
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import org.slf4j.LoggerFactory

data class EnhetRetryConfig(
    val maxRetries: Int = requiredConfigForKey("enhet.retry.max.retries").toInt(),
    val delaySeconds: Long = requiredConfigForKey("enhet.retry.delay.seconds").toLong()
)

data class LagreSakinfoPayload(
    val behandlingId: BehandlingId,
    val storedBQBehandling: BQBehandling? = null,
    val avklaringsbehovKode: String? = null,
    val retryCount: Int = 0,
    val triggerKilde: String = "ukjent",
)

class LagreSakinfoTilBigQueryJobbUtfører(
    private val sakStatistikkService: ISaksStatistikkService,
    private val jobbAppender: JobbAppender,
    private val repositoryProvider: RepositoryProvider,
    private val enhetRetryConfig: EnhetRetryConfig = EnhetRetryConfig()
) : JobbUtfører {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val payload = lesPayload(input)
        val behandlingId = payload.behandlingId

        val behandling = repositoryProvider.provide<BehandlingRepository>().hent(behandlingId)

        LoggingKontekst(behandling.referanse).use {
            utførJobb(payload)
        }
    }

    // TODO: Fjern bakoverkompatibilitet etter at alle gamle jobber er prosessert
    private fun lesPayload(input: JobbInput): LagreSakinfoPayload {
        return try {
            input.payload<LagreSakinfoPayload>()
        } catch (_: DeserializationException) {
            log.info("Deserialisering som LagreSakinfoPayload feilet, prøver gammelt format (BehandlingId)")
            val behandlingId = input.payload<BehandlingId>()
            val storedBQBehandling = try {
                input.optionalParameter("storedBQBehandling")
                    ?.let { DefaultJsonMapper.fromJson<BQBehandling>(it) }
            } catch (e: DeserializationException) {
                log.warn(
                    "Klarte ikke deserialisere storedBQBehandling fra gammelt format, ignorerer",
                    e
                )
                null
            }
            LagreSakinfoPayload(
                behandlingId = behandlingId,
                storedBQBehandling = storedBQBehandling,
                avklaringsbehovKode = input.optionalParameter("avklaringsbehovKode"),
                retryCount = input.optionalParameter("enhetRetryCount")?.toInt() ?: 0,
                triggerKilde = input.optionalParameter("triggerKilde") ?: "ukjent",
            )
        }
    }

    private fun utførJobb(payload: LagreSakinfoPayload) {
        val behandlingId = payload.behandlingId
        val retryCount = payload.retryCount
        val triggerKilde = payload.triggerKilde

        log.info(
            "Kjører: triggerKilde=$triggerKilde, retryCount=$retryCount."
        )

        val resultat = if (payload.storedBQBehandling != null) {
            sakStatistikkService.lagreMedStoredBQBehandling(
                behandlingId, payload.storedBQBehandling, payload.avklaringsbehovKode?.let(
                    Definisjon::forKode
                )
            )
        } else {
            sakStatistikkService.lagreSakInfoTilBigquery(behandlingId)
        }

        when (resultat) {
            SakStatistikkResultat.OK -> {}
            is SakStatistikkResultat.ManglerEnhet -> {
                if (retryCount < enhetRetryConfig.maxRetries) {
                    val delay = enhetRetryConfig.delaySeconds * (1L shl retryCount)
                    log.info(
                        "Enhet mangler for behandling ${resultat.behandlingId}, " +
                                "avklaringsbehov=${resultat.avklaringsbehovKode}. " +
                                "Reschedulerer om ${delay}s " +
                                "(forsøk ${retryCount + 1}/${enhetRetryConfig.maxRetries})."
                    )
                    jobbAppender.leggTilLagreSakTilBigQueryJobb(
                        repositoryProvider,
                        behandlingId,
                        delayInSeconds = delay,
                        storedBQBehandling = resultat.bqBehandling,
                        avklaringsbehovKode = resultat.avklaringsbehovKode,
                        enhetRetryCount = retryCount + 1,
                        triggerKilde = "retry($triggerKilde)",
                    )
                } else {
                    error(
                        "Enhet mangler fortsatt etter ${enhetRetryConfig.maxRetries} forsøk " +
                                "for behandling ${resultat.behandlingId}, " +
                                "avklaringsbehov=${resultat.avklaringsbehovKode}."
                    )
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
        val sakStatistikkService = lagSaksStatistikkService(repositoryProvider, gatewayProvider)
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
