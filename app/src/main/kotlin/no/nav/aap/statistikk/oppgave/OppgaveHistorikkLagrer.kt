package no.nav.aap.statistikk.oppgave

import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.enhet.EnhetRepository
import no.nav.aap.statistikk.enhet.SaksbehandlerRepository
import no.nav.aap.statistikk.person.PersonService
import org.slf4j.LoggerFactory

class OppgaveHistorikkLagrer(
    private val personService: PersonService,
    private val oppgaveRepository: OppgaveRepository,
    private val enhetRepository: EnhetRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val behandlingRepository: IBehandlingRepository,
    private val lagreSakInfotilBigQueryCallback: (BehandlingId) -> Unit
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
            oppgave.person?.let { personService.hentEllerLagrePerson(oppgave.person.ident) }

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
                if (behandling.typeBehandling != TypeBehandling.Oppfølgingsbehandling) {
                    log.info("Kaller lagreSakInfotilBigQueryCallback: $lagreSakInfotilBigQueryCallback")
                    lagreSakInfotilBigQueryCallback(behandling.id!!)
                }
            } else {
                log.info("Fant ikke behandling tilknyttet oppgaven, behandlingReferanse=${oppgave.behandlingReferanse.referanse}")
            }
        } else {
            log.info("Mangler behandlingReferanse for oppgaven, oppgaveId=${oppgave.id}")
        }
    }
}