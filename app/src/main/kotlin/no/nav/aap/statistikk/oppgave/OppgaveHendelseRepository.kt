package no.nav.aap.statistikk.oppgave

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.repository.Repository
import java.time.LocalDateTime
import java.util.*

interface OppgaveHendelseRepository : Repository {
    fun lagreHendelse(hendelse: OppgaveHendelse): Long
    fun hentHendelserForId(id: Long): List<OppgaveHendelse>
    fun hentEnhetForAvklaringsbehov(
        behandlingReferanse: UUID,
        avklaringsbehovKode: Definisjon
    ): List<EnhetOgTidspunkt>
}

data class EnhetOgTidspunkt(val enhet: String, val tidspunkt: LocalDateTime)