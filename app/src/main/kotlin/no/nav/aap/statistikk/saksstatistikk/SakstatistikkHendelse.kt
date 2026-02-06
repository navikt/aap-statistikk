package no.nav.aap.statistikk.saksstatistikk

import java.time.LocalDateTime
import java.util.*

sealed class SakstatistikkHendelse {
    abstract val behandlingReferanse: UUID
    abstract val tidspunkt: LocalDateTime
    abstract val kilde: Kilde
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
) : SakstatistikkHendelse()

data class OppgaveReservertHendelse(
    override val behandlingReferanse: UUID,
    override val tidspunkt: LocalDateTime,
    override val kilde: Kilde = Kilde.Oppgave,
    val avklaringsbehovKode: String,
    val reservertAv: String,
    val enhet: String
) : SakstatistikkHendelse()

data class OppgaveLukketHendelse(
    override val behandlingReferanse: UUID,
    override val tidspunkt: LocalDateTime,
    override val kilde: Kilde = Kilde.Oppgave,
    val avklaringsbehovKode: String
) : SakstatistikkHendelse()

data class OppgaveOpprettetHendelse(
    override val behandlingReferanse: UUID,
    override val tidspunkt: LocalDateTime,
    override val kilde: Kilde = Kilde.Oppgave,
    val avklaringsbehovKode: String,
    val enhet: String,
    val reservertAv: String? = null
) : SakstatistikkHendelse()

data class OppgaveAvreservertHendelse(
    override val behandlingReferanse: UUID,
    override val tidspunkt: LocalDateTime,
    override val kilde: Kilde = Kilde.Oppgave,
    val avklaringsbehovKode: String
) : SakstatistikkHendelse()

data class OppgaveOppdatertHendelse(
    override val behandlingReferanse: UUID,
    override val tidspunkt: LocalDateTime,
    val avklaringsbehovKode: String,
    override val kilde: Kilde = Kilde.Oppgave,
    val reservertAv: String?,
    val enhet: String
) : SakstatistikkHendelse()
