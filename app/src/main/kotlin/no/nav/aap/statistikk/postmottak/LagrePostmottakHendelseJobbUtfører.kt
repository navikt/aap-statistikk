package no.nav.aap.statistikk.postmottak

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status
import no.nav.aap.postmottak.kontrakt.behandling.TypeBehandling
import no.nav.aap.postmottak.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.lagretPostmottakHendelse
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import org.slf4j.LoggerFactory

class LagrePostmottakHendelseJobbUtfører(
    private val postmottakBehandlingService: PostmottakBehandlingService,
    private val personService: PersonService,
) :
    JobbUtfører {
    private val logger = LoggerFactory.getLogger(LagrePostmottakHendelseJobbUtfører::class.java)

    override fun utfør(input: JobbInput) {

        val hendelse = input.payload<DokumentflytStoppetHendelse>()

        val person = personService.hentEllerLagrePerson(hendelse.ident)

        val domeneHendelse = hendelse.tilDomene(person)
        val oppdatertBehandling =
            postmottakBehandlingService.oppdaterEllerOpprettBehandling(domeneHendelse)

        PrometheusProvider.prometheus.lagretPostmottakHendelse().increment()
        logger.info("Fullført prosessering av postmottak-behandling med ID ${oppdatertBehandling.id()}.")
    }
}

class LagrePostmottakHendelseJobb : Jobb {
    override fun konstruer(connection: DBConnection): LagrePostmottakHendelseJobbUtfører {
        return LagrePostmottakHendelseJobbUtfører(
            postmottakBehandlingService = PostmottakBehandlingService(
                PostmottakBehandlingRepository(
                    connection
                )
            ),
            personService = PersonService(
                PersonRepository(connection)
            ),
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
        endringer = mutableListOf(
            PostmottakOppdatering(
                gjeldende = true,
                status = this.status.name,
                oppdatertTid = this.hendelsesTidspunkt,
                sisteSaksbehandler = this.avklaringsbehov.sistePersonPåBehandling(),
                gjeldendeAvklaringsBehov = this.avklaringsbehov.utledGjeldendeAvklaringsBehov()
            )
        )
    )
}

fun TypeBehandling.tilDomene(): no.nav.aap.statistikk.behandling.TypeBehandling {
    return when (this) {
        TypeBehandling.DokumentHåndtering -> no.nav.aap.statistikk.behandling.TypeBehandling.Dokumenthåndtering
        TypeBehandling.Journalføring -> no.nav.aap.statistikk.behandling.TypeBehandling.Journalføring
    }
}

/**
 * Nøyaktig samme logikk som [no.nav.aap.statistikk.hendelser.sistePersonPåBehandling]. Finnes måte å unngå å duplisere kode?
 */
fun List<AvklaringsbehovHendelseDto>.sistePersonPåBehandling(): String? {
    return this.flatMap { it.endringer }
        .filter { it.endretAv.lowercase() != "Kelvin".lowercase() }
        .maxByOrNull { it.tidsstempel }?.endretAv
}

fun List<AvklaringsbehovHendelseDto>.utledGjeldendeAvklaringsBehov(): String? {
    return this
        .filter(function())
        .sortedByDescending {
            it.endringer.minByOrNull { endring -> endring.tidsstempel }!!.tidsstempel
        }
        .map { it.avklaringsbehovDefinisjon.kode }
        .firstOrNull()?.toString()
}

private fun function(): (AvklaringsbehovHendelseDto) -> Boolean =
    {
        setOf(
            Status.OPPRETTET,
            Status.SENDT_TILBAKE_FRA_BESLUTTER
        ).contains(it.status)
    }