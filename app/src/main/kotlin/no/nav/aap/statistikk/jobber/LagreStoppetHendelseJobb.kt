package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.motor.Jobb
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.postgresRepositoryRegistry

class LagreStoppetHendelseJobb(
    private val jobbAppender: JobbAppender,
    private val gatewayProvider: GatewayProvider,
) : Jobb {
    override fun konstruer(connection: DBConnection): LagreStoppetHendelseJobbUtfører {
        val hendelsesService = HendelsesService.konstruer(
            avsluttetBehandlingService = AvsluttetBehandlingService.konstruer(
                gatewayProvider = gatewayProvider,
                repositoryProvider = postgresRepositoryRegistry.provider(connection),
                opprettBigQueryLagringYtelseCallback = { behandlingId ->
                    jobbAppender.leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
                        connection,
                        behandlingId
                    )
                }
            ),
            jobbAppender = jobbAppender,
            repositoryProvider =postgresRepositoryRegistry.provider(connection)
        )
        return LagreStoppetHendelseJobbUtfører(hendelsesService)
    }

    override fun type(): String {
        return "statistikk.lagreHendelse"
    }

    override fun navn(): String {
        return "lagreHendelse"
    }

    override fun beskrivelse(): String {
        return "beskrivelse"
    }
}