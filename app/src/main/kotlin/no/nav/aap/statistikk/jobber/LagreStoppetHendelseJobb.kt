package no.nav.aap.statistikk.jobber

import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakService

class LagreStoppetHendelseJobb(
    private val meterRegistry: MeterRegistry,
    private val avsluttetBehandlingService: (DBConnection) -> AvsluttetBehandlingService,
    private val sakService: (DBConnection) -> SakService,
    private val personService: (DBConnection) -> PersonService,
    private val jobbAppender: JobbAppender
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        val hendelsesService = HendelsesService(
            sakService = sakService(connection),
            avsluttetBehandlingService = avsluttetBehandlingService(connection),
            personService = personService(connection),
            behandlingRepository = BehandlingRepository(connection),
            meterRegistry = meterRegistry,
            opprettBigQueryLagringSakStatistikkCallback = { behandlingId ->
                jobbAppender.leggTilLagreSakTilBigQueryJobb(
                    connection,
                    behandlingId
                )
            },
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