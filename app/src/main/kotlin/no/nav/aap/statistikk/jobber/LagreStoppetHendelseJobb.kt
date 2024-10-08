package no.nav.aap.statistikk.jobber

import io.micrometer.core.instrument.Counter
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.sak.IBigQueryKvitteringRepository
import no.nav.aap.statistikk.sak.SakRepositoryImpl

class LagreStoppetHendelseJobb(
    private val bqRepository: IBQRepository,
    private val stoppetHendelseLagretCounter: Counter,
    private val bigQueryKvitteringRepository: (DBConnection) -> IBigQueryKvitteringRepository
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        val hendelsesService = HendelsesService(
            sakRepository = SakRepositoryImpl(connection),
            personRepository = PersonRepository(connection),
            behandlingRepository = BehandlingRepository(connection),
            bigQueryRepository = bqRepository,
            bigQueryKvitteringRepository = bigQueryKvitteringRepository(connection),
        )
        return LagreStoppetHendelseJobbUtfører(
            hendelsesService,
            stoppetHendelseLagretCounter
        )
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