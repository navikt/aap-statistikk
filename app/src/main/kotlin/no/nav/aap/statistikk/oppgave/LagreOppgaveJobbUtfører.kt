package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.enhet.EnhetRepository
import no.nav.aap.statistikk.enhet.SaksbehandlerRepository
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(LagreOppgaveJobbUtfører::class.java)


class LagreOppgaveJobbUtfører(
    private val oppgaveHendelseRepository: OppgaveHendelseRepository,
    private val personService: PersonService,
    private val oppgaveRepository: OppgaveRepository,
    private val enhetRepository: EnhetRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val behandlingRepository: IBehandlingRepository,
    private val lagreSakInfotilBigQueryCallback: (BehandlingId) -> Unit
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

        val personMedId =
            oppgave.person?.let { personService.hentEllerLagrePerson(oppgave.person.ident) }

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

        if (oppgave.behandlingReferanse != null) {
            val behandling = behandlingRepository.hent(oppgave.behandlingReferanse.referanse)

            if (behandling != null) {
                logger.info("Kaller lagreSakInfotilBigQueryCallback: $lagreSakInfotilBigQueryCallback")
                lagreSakInfotilBigQueryCallback(behandling.id!!)
            } else {
                logger.info("Fant ikke behandling tilknyttet oppgaven, behandlingReferanse=${oppgave.behandlingReferanse.referanse}")
            }
        } else {
            logger.info("Mangler behandlingReferanse for oppgaven, oppgaveId=${oppgave.id}")
        }
    }
}

class LagreOppgaveJobb(private val jobbAppender: JobbAppender) : Jobb {
    override fun beskrivelse(): String {
        return "Henter rene oppgavehendelser fra databasen og konverterer til modell."
    }

    override fun konstruer(connection: DBConnection): LagreOppgaveJobbUtfører {
        return LagreOppgaveJobbUtfører(
            OppgaveHendelseRepository(connection),
            PersonService(PersonRepository(connection)),
            OppgaveRepository(connection),
            EnhetRepository(connection),
            SaksbehandlerRepository(connection),
            BehandlingRepository(connection),
            { jobbAppender.leggTilLagreSakTilBigQueryJobb(connection, it) }
        )
    }

    override fun navn(): String {
        return "Konverter oppgavehendelser til modell"
    }

    override fun type(): String {
        return "statistikk.konverterOppgavehendelserTilModell"
    }
}