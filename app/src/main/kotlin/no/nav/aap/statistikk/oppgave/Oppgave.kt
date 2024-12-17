package no.nav.aap.statistikk.oppgave

import no.nav.aap.oppgave.verdityper.Status
import no.nav.aap.statistikk.person.Person
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

private val logger = LoggerFactory.getLogger("oppgave.oppgave")

enum class Oppgavestatus {
    OPPRETTET, AVSLUTTET
}

data class Oppgave(
    val id: Long? = null,
    val enhet: Enhet,
    val person: Person?,
    val status: Oppgavestatus,
    val opprettetTidspunkt: LocalDateTime,
    val reservasjon: Reservasjon? = null,
    val hendelser: List<OppgaveHendelse>
)

class Reservasjon(val reservertAv: Saksbehandler, val reservasjonOpprettet: LocalDateTime)

enum class HendelseType {
    OPPRETTET,
    RESERVERT,
    AVRESERVERT,
    LUKKET
}

/**
 * Dette er den persisterte modellen. Ha gjeldende = true - felt.
 */
data class OppgaveHendelse(
    val hendelse: HendelseType,
    val mottattTidspunkt: LocalDateTime,
    val personIdent: String? = null,
    val saksnummer: String? = null,
    val behandlingRef: UUID? = null,
    val journalpostId: Long? = null,
    val enhet: String,
    val avklaringsbehovKode: String,
    val status: Oppgavestatus,
    val reservertAv: String? = null,
    val reservertTidspunkt: LocalDateTime? = null,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
) {
    init {
        require(if (reservertAv != null) reservertTidspunkt != null else true)
    }
}

/**
 * Event sourcing ;)
 */
fun List<OppgaveHendelse>.tilOppgave(): Oppgave {
    return this.sortedBy { it.mottattTidspunkt }
        .fold<OppgaveHendelse, Oppgave?>(null) { acc, hendelse ->
            if (acc == null) {
                val reservasjon =
                    reservasjon(hendelse)
                Oppgave(
                    enhet = Enhet(hendelse.enhet),
                    person = hendelse.personIdent?.let { Person(it) },
                    status = hendelse.status,
                    opprettetTidspunkt = hendelse.mottattTidspunkt,
                    hendelser = listOf(hendelse),
                    reservasjon = reservasjon
                )
            } else {
                if (hendelse.personIdent != null && acc.person != null && hendelse.personIdent != acc.person.ident) {
                    logger.warn("Person har endret seg på oppgave. Var ${acc.person}, nå ${hendelse.personIdent}")
                }
                acc.copy(
                    hendelser = acc.hendelser + hendelse,
                    enhet = Enhet(hendelse.enhet),
                    status = hendelse.status,
                    reservasjon = reservasjon(hendelse)
                )
            }
        }!!
}

private fun reservasjon(hendelse: OppgaveHendelse) =
    if (hendelse.reservertAv != null && hendelse.reservertTidspunkt != null) {
        Reservasjon(
            Saksbehandler(hendelse.reservertAv),
            hendelse.reservertTidspunkt
        )
    } else null

data class Enhet(val kode: String)

class Saksbehandler(val ident: String)