package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import org.slf4j.LoggerFactory

class LagreStoppetHendelseJobb(
    private val hendelsesRepository: HendelsesRepository
) : JobbUtfører {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val dto = DefaultJsonMapper.fromJson<StoppetBehandling>(input.payload())
        logger.info("Got message: $dto")

        hendelsesRepository.lagreHendelse(dto)
    }

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            val hendelsesRepository = HendelsesRepository(connection, SakRepositoryImpl(connection))
            return LagreStoppetHendelseJobb(hendelsesRepository)
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
}