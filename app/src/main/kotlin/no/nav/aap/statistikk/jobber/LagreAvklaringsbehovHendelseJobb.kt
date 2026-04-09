package no.nav.aap.statistikk.jobber

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProvidersJobbSpesifikasjon
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.hendelser.ResendHendelseService
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.jobber.appender.MotorHendelsePublisher
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakService

class LagreAvklaringsbehovHendelseJobb(
    private val jobbAppender: JobbAppender,
) : ProvidersJobbSpesifikasjon {


    override fun konstruer(
        repositoryProvider: RepositoryProvider,
        gatewayProvider: GatewayProvider,
    ): JobbUtfører {
        val hendelsePublisher = MotorHendelsePublisher.utenYtelseJobb(
            jobbAppender = jobbAppender,
            repositoryProvider = repositoryProvider,
        )
        return LagreAvklaringsbehovHendelseJobbUtfører(
            ResendHendelseService(
                sakService = SakService(repositoryProvider),
                personService = PersonService(repositoryProvider),
                behandlingRepository = repositoryProvider.provide(),
                behandlingService = BehandlingService(repositoryProvider, gatewayProvider),
                hendelsePublisher = hendelsePublisher,
            )
        )
    }

    override val type = "statistikk.lagreAvklaringsbehovHendelseJobb"
    override val navn = "LagreAvklaringsbehovHendelseJobb"
    override val beskrivelse = "Jobb for å regenerere behandlinghistorikk fra behandlingsflyt."

}

private class LagreAvklaringsbehovHendelseJobbUtfører(
    private val resendHendelseService: ResendHendelseService
) : JobbUtfører {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val dto = DefaultJsonMapper.fromJson<StoppetBehandling>(input.payload())
        logger.info("StoppetBehandling mottatt Regenerer statistikk. Behandlingsreferanse: ${dto.behandlingReferanse}.")

        if (!Miljø.erProd()) {
            val hendelseJSON = DefaultJsonMapper.toJson(dto)
            logger.info("Regenerer statistikk for behandling ${dto.behandlingReferanse}. Hendelse: $hendelseJSON")
        }
        resendHendelseService.prosesserNyHistorikkHendelse(dto)
        logger.info("Ferdig å regenerere statistikk for behandling ${dto.behandlingReferanse}.")
    }
}
