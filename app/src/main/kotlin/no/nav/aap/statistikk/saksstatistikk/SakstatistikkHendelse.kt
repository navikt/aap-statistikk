package no.nav.aap.statistikk.saksstatistikk

import java.time.LocalDateTime
import java.util.*

sealed interface SakstatistikkHendelse : Comparable<SakstatistikkHendelse> {
    val behandlingReferanse: UUID
    val tidspunkt: LocalDateTime
    val kilde: Kilde
}

enum class Kilde {
    Behandling, Oppgave
}

data class BehandlingsflytHendelse(
    override val behandlingReferanse: UUID,
    override val tidspunkt: LocalDateTime,
    override val kilde: Kilde = Kilde.Behandling,
    val status: String,
    val avklaringsbehov: String?,
    val sisteLøsteAvklaringsbehov: String?,
    val sisteSaksbehandlerPåBehandling: String?
) : SakstatistikkHendelse {
    override fun compareTo(other: SakstatistikkHendelse): Int {
        return tidspunkt.compareTo(other.tidspunkt)
    }
}

sealed interface OppgaveHendelse : SakstatistikkHendelse {
    val mottattTidspunkt: LocalDateTime

    override fun compareTo(other: SakstatistikkHendelse): Int {
        return when {
            other is OppgaveHendelse -> if (this.tidspunkt != other.tidspunkt) {
                this.tidspunkt.compareTo(other.tidspunkt)
            } else {
                this.mottattTidspunkt.compareTo(other.mottattTidspunkt)
            }

            else -> this.tidspunkt.compareTo(other.tidspunkt)
        }

    }
}

data class OppgaveReservertHendelse(
    override val behandlingReferanse: UUID,
    override val tidspunkt: LocalDateTime,
    override val kilde: Kilde = Kilde.Oppgave,
    override val mottattTidspunkt: LocalDateTime,
    val avklaringsbehovKode: String,
    val reservertAv: String,
    val enhet: String
) : OppgaveHendelse

data class OppgaveLukketHendelse(
    override val behandlingReferanse: UUID,
    override val tidspunkt: LocalDateTime,
    override val mottattTidspunkt: LocalDateTime,
    override val kilde: Kilde = Kilde.Oppgave,
    val avklaringsbehovKode: String
) : OppgaveHendelse

data class OppgaveOpprettetHendelse(
    override val behandlingReferanse: UUID,
    override val tidspunkt: LocalDateTime,
    override val mottattTidspunkt: LocalDateTime,
    override val kilde: Kilde = Kilde.Oppgave,
    val avklaringsbehovKode: String,
    val enhet: String,
    val reservertAv: String? = null
) : OppgaveHendelse

data class OppgaveAvreservertHendelse(
    override val behandlingReferanse: UUID,
    override val tidspunkt: LocalDateTime,
    override val mottattTidspunkt: LocalDateTime,
    override val kilde: Kilde = Kilde.Oppgave,
    val avklaringsbehovKode: String
) : OppgaveHendelse

data class OppgaveOppdatertHendelse(
    override val behandlingReferanse: UUID,
    override val tidspunkt: LocalDateTime,
    override val mottattTidspunkt: LocalDateTime,
    val avklaringsbehovKode: String,
    override val kilde: Kilde = Kilde.Oppgave,
    val reservertAv: String?,
    val enhet: String
) : OppgaveHendelse
