package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProvidersJobbSpesifikasjon
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.jobber.appender.JobbAppender

class LagreStoppetHendelseJobb(
    private val jobbAppender: JobbAppender,
    private val lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb
) : ProvidersJobbSpesifikasjon {
    override fun konstruer(
        repositoryProvider: RepositoryProvider,
        gatewayProvider: GatewayProvider
    ): JobbUtfører {
        val hendelsesService = HendelsesService.konstruer(
            avsluttetBehandlingService = AvsluttetBehandlingService.konstruer(
                gatewayProvider = gatewayProvider,
                repositoryProvider = repositoryProvider,
                opprettBigQueryLagringYtelseCallback = { behandlingId ->
                    jobbAppender.leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
                        repositoryProvider,
                        behandlingId,
                        lagreAvsluttetBehandlingTilBigQueryJobb
                    )
                }
            ),
            jobbAppender = jobbAppender,
            repositoryProvider = repositoryProvider
        )
        return LagreStoppetHendelseJobbUtfører(hendelsesService)
    }

    override val type = "statistikk.lagreHendelse"

    override val navn = "lagreHendelse"
    override val beskrivelse = "beskrivelse"

}