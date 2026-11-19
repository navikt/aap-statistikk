package no.nav.aap.statistikk.jobber

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProvidersJobbSpesifikasjon
import no.nav.aap.statistikk.hendelser.RebyggBehandlingshistorikkService
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.jobber.appender.MotorHendelsePublisher
import org.slf4j.LoggerFactory

class RebyggBehandlingshistorikkJobb(private val jobbAppender: JobbAppender) : ProvidersJobbSpesifikasjon {

    override fun konstruer(repositoryProvider: RepositoryProvider, gatewayProvider: GatewayProvider): JobbUtfører {
        val hendelsePublisher = MotorHendelsePublisher.utenYtelseJobb(
            jobbAppender = jobbAppender,
            repositoryProvider = repositoryProvider,
        )
        return RebyggBehandlingshistorikkJobbUtfører(
            RebyggBehandlingshistorikkService.konstruer(repositoryProvider, gatewayProvider, hendelsePublisher),
        )
    }

    override val type = "statistikk.lagreAvklaringsbehovHendelseJobb"
    override val navn = "RebyggBehandlingshistorikkJobb"
    override val beskrivelse = "Bygger behandlingshistorikk på nytt fra opplysninger sendt av Behandlingsflyt."
}

private class RebyggBehandlingshistorikkJobbUtfører(private val historikkService: RebyggBehandlingshistorikkService) :
    JobbUtfører {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val dto = DefaultJsonMapper.fromJson<StoppetBehandling>(input.payload())
        logger.info("Bygger behandlingshistorikk for behandling ${dto.behandlingReferanse}.")

        if (!Miljø.erProd()) {
            val hendelseJSON = DefaultJsonMapper.toJson(dto)
            logger.info("Behandlingsreferanse: ${dto.behandlingReferanse}. Historikkgrunnlag: $hendelseJSON")
        }
        historikkService.rebyggHistorikk(dto)
        logger.info("Ferdig med å bygge behandlingshistorikk for behandling ${dto.behandlingReferanse}.")
    }
}
