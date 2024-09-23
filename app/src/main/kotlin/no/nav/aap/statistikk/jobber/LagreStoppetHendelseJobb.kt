package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakRepository
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import org.slf4j.LoggerFactory

class LagreStoppetHendelseJobb(
    private val hendelsesRepository: IHendelsesRepository,
    private val sakRepository: SakRepository,
    private val personRepository: PersonRepository,
    private val behandlingRepository: BehandlingRepository,
) : JobbUtfører {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val dto = DefaultJsonMapper.fromJson<StoppetBehandling>(input.payload())
        logger.info("Got message: $dto")

        val person = hentEllerSettInnPerson(dto)
        var sak = hentEllerSettInnSak(dto, person)

        val behandlingId = hentEllerLagreBehandlingId(dto, sak)

        hendelsesRepository.lagreHendelse(dto, sak?.id!!, behandlingId)
    }

    private fun hentEllerLagreBehandlingId(
        dto: StoppetBehandling,
        sak: Sak?
    ): Long {
        val behandling = behandlingRepository.hent(dto.behandlingReferanse)
        val behandlingId = if (behandling != null) {
            behandling.id!!
        } else {
            behandlingRepository.lagre(
                Behandling(
                    referanse = dto.behandlingReferanse,
                    sak = sak!!,
                    typeBehandling = dto.behandlingType,
                    opprettetTid = dto.behandlingOpprettetTidspunkt
                )
            )
        }
        return behandlingId
    }

    private fun hentEllerSettInnSak(
        dto: StoppetBehandling,
        person: Person
    ): Sak? {
        var sak = sakRepository.hentSak(dto.saksnummer)
        if (sak == null) {
            val sakId = sakRepository.settInnSak(
                Sak(
                    id = null,
                    saksnummer = dto.saksnummer,
                    person = person
                )
            )
            sak = sakRepository.hentSak(sakId)
        }
        return sak
    }

    private fun hentEllerSettInnPerson(dto: StoppetBehandling): Person {
        var person = personRepository.hentPerson(dto.ident)
        if (person == null) {
            personRepository.lagrePerson(Person(dto.ident))
        }
        person = personRepository.hentPerson(dto.ident)!!
        return person
    }

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            val hendelsesRepository = HendelsesRepository(
                connection
            )
            return LagreStoppetHendelseJobb(
                hendelsesRepository,
                SakRepositoryImpl(connection),
                PersonRepository(connection),
                BehandlingRepository(connection)
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
}