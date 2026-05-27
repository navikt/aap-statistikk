package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.repository.Repository
import java.time.LocalDateTime
import java.util.*

interface OppgaveHendelseRepository : Repository {
    fun lagreHendelse(hendelse: OppgaveHendelse): Long
    fun sisteVersjonForId(id: Long): Long?
    fun hentHendelserForId(id: Long): List<OppgaveHendelse>
    fun hentSisteCutoffAnchorForAvklaringsbehov(
        behandlingReferanse: UUID,
        avklaringsbehovKode: String,
        behandlingTidspunkt: LocalDateTime,
        preferertOppgaveId: Long?
    ): OppgaveCutoffAnchor?

    fun hentEnhetOgReservasjonForAvklaringsbehov(
        behandlingReferanse: UUID,
        avklaringsbehovKode: String
    ): List<EnhetReservasjonOgTidspunkt>
}

data class OppgaveCutoffAnchor(
    val oppgaveId: Long,
    val versjon: Long,
    val sendtTid: LocalDateTime
)

data class EnhetReservasjonOgTidspunkt(
    val enhet: String,
    val tidspunkt: LocalDateTime,
    val reservertAv: String?
)