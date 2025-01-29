package no.nav.aap.statistikk.postmottak

import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.postmottak.kontrakt.behandling.TypeBehandling
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.statistikk.lagretPostmottakHendelse
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import org.slf4j.LoggerFactory

class LagrePostmottakHendelseJobbUtfører(
    private val postmottakBehandlingRepository: PostmottakBehandlingRepository,
    private val personService: PersonService,
    private val meterRegistry: MeterRegistry
) :
    JobbUtfører {
    private val logger = LoggerFactory.getLogger(LagrePostmottakHendelseJobbUtfører::class.java)

    override fun utfør(input: JobbInput) {

        val hendelse = input.payload<DokumentflytStoppetHendelse>()

        val person = personService.hentEllerLagrePerson(hendelse.ident)

        val domeneHendelse = hendelse.tilDomene(person)

        val eksisterendeBehandling =
            postmottakBehandlingRepository.hentEksisterendeBehandling(domeneHendelse.referanse)

        val oppdatertBehandling = if (eksisterendeBehandling == null) {
            val id =
                postmottakBehandlingRepository.opprettBehandling(behandling = domeneHendelse)
            domeneHendelse.medId(id = id)
        } else {
            postmottakBehandlingRepository.oppdaterBehandling(
                domeneHendelse.referanse,
                behandling = domeneHendelse.endringer().first() // "Only"
            )
            postmottakBehandlingRepository.hentEksisterendeBehandling(domeneHendelse.referanse)!!
        }

        meterRegistry.lagretPostmottakHendelse().increment()
        logger.info("Fullført prosessering av postmottak-behandling med ID ${oppdatertBehandling.id()}.")
    }
}

class LagrePostmottakHendelseJobb(private val meterRegistry: MeterRegistry) : Jobb {
    override fun konstruer(connection: DBConnection): LagrePostmottakHendelseJobbUtfører {
        return LagrePostmottakHendelseJobbUtfører(
            postmottakBehandlingRepository = PostmottakBehandlingRepository(connection),
            personService = PersonService(
                PersonRepository(connection)
            ),
            meterRegistry = meterRegistry
        )
    }

    override fun navn(): String {
        return "Konverter postmottakhendelser"
    }

    override fun type(): String {
        return "statistikk.lagrePostmottakHendelse"
    }

    override fun beskrivelse(): String {
        return "Konverter oppgavehendelser"
    }
}

fun DokumentflytStoppetHendelse.tilDomene(person: Person): PostmottakBehandling {
    requireNotNull(person.id())
    return PostmottakBehandling(
        journalpostId = this.journalpostId.referanse,
        person = person,
        referanse = this.referanse,
        behandlingType = this.behandlingType.tilDomene(),
        mottattTid = this.hendelsesTidspunkt,
    )
}

fun TypeBehandling.tilDomene(): no.nav.aap.statistikk.behandling.TypeBehandling {
    return when (this) {
        TypeBehandling.DokumentHåndtering -> no.nav.aap.statistikk.behandling.TypeBehandling.Dokumenthåndtering
        TypeBehandling.Journalføring -> no.nav.aap.statistikk.behandling.TypeBehandling.Journalføring
    }
}