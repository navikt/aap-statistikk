package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProvidersJobbSpesifikasjon
import no.nav.aap.statistikk.behandling.BehandlingId
import org.slf4j.LoggerFactory

class ResendSakstatistikkJobbUtfører(
    val sakStatikkService: SaksStatistikkService
) : JobbUtfører {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val behandlingId = input.payload<Long>().let(::BehandlingId)
        log.info("Resender sakstatistikk for behandling med id $behandlingId.")

        val alleHendelser = sakStatikkService.alleHendelserPåBehandling(behandlingId)
            .map { it.copy(erResending = true) }

        alleHendelser.forEach {
            sakStatikkService.lagreBQBehandling(it)
        }
        log.info("Lagret ${alleHendelser.size} hendelser i sakssakstatistikk-tabell.")
    }
}

class ResendSakstatistikkJobb : ProvidersJobbSpesifikasjon {

    override fun konstruer(
        repositoryProvider: RepositoryProvider,
        gatewayProvider: GatewayProvider
    ): JobbUtfører {
        val sakStatistikkService =
            SaksStatistikkService.konstruer(
                gatewayProvider,
                repositoryProvider
            )
        return ResendSakstatistikkJobbUtfører(
            sakStatikkService = sakStatistikkService,
        )
    }

    override val type: String = "statistikk.resendSakstatistikk"
    override val navn: String = "Resend saksstatikk"
    override val beskrivelse: String = "Resend saksstatikk"

}