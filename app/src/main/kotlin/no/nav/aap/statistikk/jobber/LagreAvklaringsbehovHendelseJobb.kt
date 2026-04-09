package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProvidersJobbSpesifikasjon
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.hendelser.ResendHendelseService
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakService
import org.slf4j.LoggerFactory

class LagreAvklaringsbehovHendelseJobb(
    private val jobbAppender: JobbAppender,
) : ProvidersJobbSpesifikasjon {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun konstruer(
        repositoryProvider: RepositoryProvider,
        gatewayProvider: GatewayProvider,
    ): JobbUtfører {
        val resendHendelseService = ResendHendelseService(
            sakService = SakService(repositoryProvider),
            personService = PersonService(repositoryProvider),
            behandlingRepository = repositoryProvider.provide<IBehandlingRepository>(),
            behandlingService = BehandlingService(repositoryProvider, gatewayProvider),
            opprettRekjørSakstatistikkCallback = { behandlingId ->
                log.info("Starter resending-jobb. BehandlingId: $behandlingId")
                jobbAppender.leggTilResendSakstatistikkJobb(repositoryProvider, behandlingId)
            }
        )
        return LagreAvklaringsbehovHendelseJobbUtfører(resendHendelseService)
    }

    override val type = "statistikk.lagreAvklaringsbehovHendelseJobb"
    override val navn = "LagreAvklaringsbehovHendelseJobb"
    override val beskrivelse = "Jobb for å regenerere behandlinghistorikk fra behandlingsflyt."

}