package no.nav.aap.statistikk.oppgave

import no.nav.aap.statistikk.enhet.Enhet
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
    val identifikator: Long,
    val avklaringsbehov: String,
    val enhet: Enhet,
    val person: Person?,
    val status: Oppgavestatus,
    val opprettetTidspunkt: LocalDateTime,
    val reservasjon: Reservasjon? = null,
    val behandlingReferanse: BehandlingReferanse? = null,
    val hendelser: List<OppgaveHendelse>
)

data class BehandlingReferanse(val id: Long? = null, val referanse: UUID)

data class Reservasjon(
    val id: Long? = null,
    val reservertAv: Saksbehandler,
    val reservasjonOpprettet: LocalDateTime
)

enum class HendelseType {
    OPPRETTET,
    RESERVERT,
    AVRESERVERT,
    LUKKET,
    OPPDATERT
}

/**
 * Dette er den persisterte modellen. Ha gjeldende = true - felt.
 */
data class OppgaveHendelse(
    val hendelse: HendelseType,
    val oppgaveId: Long,
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
    require(this.isNotEmpty())

    return this.sortedBy { it.mottattTidspunkt }
        .fold(null) { acc, hendelse ->
            if (acc == null) {
                val enhet = hendelse.enhet
                val reservasjon =
                    reservasjon(hendelse)
                Oppgave(
                    enhet = Enhet(kode = enhet),
                    person = hendelse.personIdent?.let { Person(it) },
                    status = hendelse.status,
                    opprettetTidspunkt = hendelse.mottattTidspunkt,
                    hendelser = listOf(hendelse),
                    behandlingReferanse = hendelse.behandlingRef?.let {
                        BehandlingReferanse(
                            referanse = it
                        )
                    },
                    reservasjon = reservasjon,
                    identifikator = hendelse.oppgaveId,
                    avklaringsbehov = hendelse.avklaringsbehovKode
                )
            } else {
                if (hendelse.personIdent != null && acc.person != null && hendelse.personIdent != acc.person.ident) {
                    logger.warn("Person har endret seg på en oppgave. Var ${acc.person.id()}, nå forskjellig.")
                }
                if (hendelse.behandlingRef != null && acc.behandlingReferanse != null && hendelse.behandlingRef != acc.behandlingReferanse.referanse) {
                    logger.warn("Behandlings-referanse har endret seg på en oppgave. Var ${acc.behandlingReferanse}, nå ${hendelse.behandlingRef}")
                }
                require(hendelse.oppgaveId == acc.identifikator) { "Skal kun aggregere oppgaver med samme id. Fikk ${hendelse.oppgaveId} og ${acc.id}." }
                if (hendelse.avklaringsbehovKode != acc.avklaringsbehov) {
                    logger.warn("Fant oppgave med ikke-unikt avklaringsbehov. Ignorerer ${hendelse.avklaringsbehovKode}, beholder ${acc.avklaringsbehov}.")
                }

                acc.copy(
                    hendelser = acc.hendelser + hendelse,
                    enhet = Enhet(kode = hendelse.enhet),
                    status = hendelse.status,
                    reservasjon = reservasjon(hendelse)
                )
            }
        }!!
}

private fun reservasjon(hendelse: OppgaveHendelse) =
    if (hendelse.reservertAv != null && hendelse.reservertTidspunkt != null) {
        Reservasjon(
            reservertAv = Saksbehandler(ident = hendelse.reservertAv),
            reservasjonOpprettet = hendelse.reservertTidspunkt
        )
    } else null

data class Saksbehandler(val id: Long? = null, val ident: String) {
    constructor(ident: String) : this(ident = ident, id = null)
}