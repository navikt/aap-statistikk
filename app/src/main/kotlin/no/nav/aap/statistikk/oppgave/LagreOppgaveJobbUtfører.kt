package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.enhet.EnhetRepository
import no.nav.aap.statistikk.enhet.SaksbehandlerRepository
import no.nav.aap.statistikk.person.PersonRepository
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(LagreOppgaveJobbUtfører::class.java)


class LagreOppgaveJobbUtfører(
    private val oppgaveHendelseRepository: OppgaveHendelseRepository,
    private val personRepository: PersonRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val enhetRepository: EnhetRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository
) :
    JobbUtfører {
    override fun utfør(input: JobbInput) {
        val hendelse = DefaultJsonMapper.fromJson<Long>(input.payload())

        val hendelser = oppgaveHendelseRepository.hentHendelserForId(hendelse)

        val oppgave = hendelser.tilOppgave()

        val saksbehandlerMedId = oppgave.reservasjon?.let {
            saksbehandlerRepository.hentSaksbehandler(it.reservertAv.ident)
                ?: Saksbehandler(
                    id = saksbehandlerRepository.lagreSaksbehandler(it.reservertAv),
                    it.reservertAv.ident
                )
        }

        val personMedId = oppgave.person?.let {
            personRepository.hentPerson(it.ident) ?: it.copy(id = personRepository.lagrePerson(it))
        }

        val enhetMedId = oppgave.enhet.let {
            val id = enhetRepository.lagreEnhet(it)
            val medId = it.copy(id = id)
            logger.info("Lagret enhet $medId")

            medId
        }

        val eksisterendeOppgave = oppgaveRepository.hentOppgave(oppgave.identifikator)


        val oppgaveMedOppdaterteFelter = oppgave.copy(
            enhet = enhetMedId,
            person = personMedId,
            reservasjon = oppgave.reservasjon?.copy(reservertAv = saksbehandlerMedId!!)
        )

        if (eksisterendeOppgave != null) {
            oppgaveRepository.oppdaterOppgave(oppgaveMedOppdaterteFelter.copy(id = eksisterendeOppgave.id))
        } else {
            oppgaveRepository.lagreOppgave(oppgaveMedOppdaterteFelter)
        }
    }

    companion object : Jobb {
        override fun beskrivelse(): String {
            return "Henter rene oppgavehendelser fra databasen og konverterer til modell."
        }

        override fun konstruer(connection: DBConnection): LagreOppgaveJobbUtfører {
            return LagreOppgaveJobbUtfører(
                OppgaveHendelseRepository(connection),
                PersonRepository(connection),
                OppgaveRepository(connection),
                EnhetRepository(connection),
                SaksbehandlerRepository(connection)
            )
        }

        override fun navn(): String {
            return "Konverter oppgavehendelser til modell"
        }

        override fun type(): String {
            return "konverterOppgavehendelserTilModell"
        }
    }
}