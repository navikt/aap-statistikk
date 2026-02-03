package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProvidersJobbSpesifikasjon
import no.nav.aap.statistikk.hendelser.ResendHendelseService
import no.nav.aap.statistikk.jobber.appender.JobbAppender

class LagreAvklaringsbehovHendelseJobb(
    private val jobbAppender: JobbAppender,
) : ProvidersJobbSpesifikasjon {

    override fun konstruer(
        repositoryProvider: RepositoryProvider,
        gatewayProvider: GatewayProvider,
    ): JobbUtfører {
        return LagreAvklaringsbehovHendelseJobbUtfører(
            ResendHendelseService.konstruer(
                repositoryProvider,
                gatewayProvider,
                jobbAppender
            )
        )
    }

    override val type = "statistikk.lagreAvklaringsbehovHendelseJobb"
    override val navn = "LagreAvklaringsbehovHendelseJobb"
    override val beskrivelse = "Jobb for å regenerere behandlinghistorikk fra behandlingsflyt."

}