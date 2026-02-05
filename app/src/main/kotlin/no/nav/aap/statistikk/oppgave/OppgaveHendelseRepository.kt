package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.repository.Repository
import java.time.LocalDateTime
import java.util.*

interface OppgaveHendelseRepository : Repository {
    fun lagreHendelse(hendelse: OppgaveHendelse): Long
    fun sisteVersjonForId(id: Long): Long?
    fun hentHendelserForId(id: Long): List<OppgaveHendelse>
    fun hentEnhetOgReservasjonForAvklaringsbehov(
        behandlingReferanse: UUID,
        avklaringsbehovKode: String
    ): List<EnhetReservasjonOgTidspunkt>

    fun hentSisteEnhetPåBehandling(behandlingReferanse: UUID): Pair<EnhetReservasjonOgTidspunkt, String>?
}

data class EnhetReservasjonOgTidspunkt(
    val enhet: String,
    val tidspunkt: LocalDateTime,
    val reservertAv: String?
)