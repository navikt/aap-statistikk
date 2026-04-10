package no.nav.aap.statistikk.oppgave

import no.nav.aap.statistikk.enhet.EnhetRepository
import no.nav.aap.statistikk.enhet.SaksbehandlerRepository
import no.nav.aap.statistikk.person.IPersonRepository
import org.slf4j.LoggerFactory

class OppgaveHistorikkLagrer(
    private val personRepository: IPersonRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val enhetRepository: EnhetRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun lagre(oppgave: Oppgave) {
        val saksbehandlerMedId = oppgave.reservasjon?.let {
            saksbehandlerRepository.hentSaksbehandler(it.reservertAv.ident)
                ?: Saksbehandler(
                    id = saksbehandlerRepository.lagreSaksbehandler(it.reservertAv),
                    it.reservertAv.ident
                )
        }

        val personMedId =
            oppgave.person?.let { personRepository.hentEllerLagre(oppgave.person.ident) }

        val enhetMedId = oppgave.enhet.let {
            val id = enhetRepository.lagreEnhet(it)
            val medId = it.copy(id = id)
            log.info("Lagret enhet $medId")

            medId
        }

        val eksisterendeOppgave = oppgaveRepository.hentOppgave(oppgave.identifikator)

        val oppgaveMedOppdaterteFelter = oppgave.copy(
            enhet = enhetMedId,
            person = personMedId,
            reservasjon = oppgave.reservasjon?.copy(reservertAv = requireNotNull(saksbehandlerMedId) { "Forventer at saksbehandler har ID." })
        )

        if (eksisterendeOppgave != null) {
            oppgaveRepository.oppdaterOppgave(oppgaveMedOppdaterteFelter.copy(id = eksisterendeOppgave.id))
        } else {
            oppgaveRepository.lagreOppgave(oppgaveMedOppdaterteFelter)
        }
    }
}