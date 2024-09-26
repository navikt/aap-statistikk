package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.bigquery.BQRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.sak.SakRepositoryImpl

class LagreStoppetHendelseJobb(private val bqRepository: IBQRepository) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        val hendelsesService = HendelsesService(
            hendelsesRepository = HendelsesRepository(connection),
            sakRepository = SakRepositoryImpl(connection),
            personRepository = PersonRepository(connection),
            behandlingRepository = BehandlingRepository(connection),
            bigQueryRepository = bqRepository
        )
        return LagreStoppetHendelseJobbUtfører(hendelsesService)
    }

    override fun type(): String {
        return "lagreHendelse"
    }

    override fun navn(): String {
        return "lagreHendelse"
    }

    override fun beskrivelse(): String {
        return "beskrivelse"
    }
}