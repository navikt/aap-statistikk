package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.enhet.Enhet

interface OppgaveRepository : Repository {
    fun lagreOppgave(oppgave: Oppgave): Long
    fun oppdaterOppgave(oppgave: Oppgave)
    fun hentOppgaverForEnhet(enhet: Enhet): List<Oppgave>
    fun hentOppgave(identifikator: Long): Oppgave?
    fun hentOppgaverForBehandling(behandlingId: BehandlingId): List<Oppgave>
}