package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.behandling.AvklaringsbehovKode
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.løsesAv
import no.nav.aap.statistikk.oppgave.HendelseType
import no.nav.aap.statistikk.oppgave.Oppgave
import no.nav.aap.statistikk.oppgave.OppgaveHendelse
import java.time.LocalDateTime
import java.util.*

object SakstatistikkEventSourcing {

    fun byggSakstatistikkHendelser(behandling: Behandling, oppgaver: List<Oppgave>): List<SakstatistikkSnapshot> {
        val alleSakstatistikkHendelser =
            konverterBehandlingHendelser(behandling) +
                konverterOppgaveHendelser(oppgaver)

        val sorterteHendelser = alleSakstatistikkHendelser.sorted()

        return byggSnapshots(sorterteHendelser)
    }

    private fun konverterBehandlingHendelser(behandling: Behandling): List<SakstatistikkHendelse> =
        behandling.hendelser.map { hendelse ->
            val tilstand = hendelse.tilstand
            val rolleEndret = rolleEndretMellom(tilstand.sisteLøsteAvklaringsbehov, tilstand.gjeldendeAvklaringsBehov)
            BehandlingsflytHendelse(
                behandlingReferanse = behandling.referanse,
                tidspunkt = hendelse.hendelsesTidspunkt,
                status = tilstand.status.name,
                avklaringsbehov = tilstand.gjeldendeAvklaringsBehov,
                sisteLøsteAvklaringsbehov = tilstand.sisteLøsteAvklaringsbehov,
                sisteSaksbehandlerPåBehandling = if (rolleEndret) null else tilstand.sisteSaksbehandlerSomLøstebehov,
            )
        }

    /**
     * Returnerer true dersom avklaringsbehovene løses av ulike roller, slik at saksbehandler-fallback
     * ikke skal bæres over fra ett steg til et annet.
     */
    private fun rolleEndretMellom(
        forrigeAvklaringsbehov: AvklaringsbehovKode?,
        nyttAvklaringsbehov: AvklaringsbehovKode?,
    ): Boolean {
        if (forrigeAvklaringsbehov == null || nyttAvklaringsbehov == null) return false
        val forrigeRoller = forrigeAvklaringsbehov.løsesAv()
        val nyeRoller = nyttAvklaringsbehov.løsesAv()
        return forrigeRoller.intersect(nyeRoller).isEmpty()
    }

    private fun konverterOppgaveHendelser(oppgaver: List<Oppgave>): List<SakstatistikkHendelse> = oppgaver.flatMap {
            oppgave,
        ->
        oppgave.hendelser.mapNotNull { hendelse ->
            konverterOppgaveHendelse(oppgave, hendelse)
        }
    }

    private fun konverterOppgaveHendelse(oppgave: Oppgave, hendelse: OppgaveHendelse): SakstatistikkHendelse? {
        val behandlingReferanse = oppgave.behandlingReferanse ?: return null

        return when (hendelse.hendelse) {
            HendelseType.OPPRETTET -> opprettetHendelse(
                behandlingReferanse.referanse,
                hendelse,
                oppgave,
            )

            HendelseType.RESERVERT -> reservertHendelse(
                behandlingReferanse.referanse,
                hendelse,
                oppgave,
            )

            HendelseType.LUKKET -> lukketHendelse(behandlingReferanse.referanse, hendelse, oppgave)

            HendelseType.AVRESERVERT -> avreservertHendelse(
                behandlingReferanse.referanse,
                hendelse,
                oppgave,
            )

            HendelseType.OPPDATERT -> oppdatertHendelse(
                behandlingReferanse.referanse,
                hendelse,
                oppgave,
            )
        }
    }

    private fun opprettetHendelse(behandlingReferanse: UUID, hendelse: OppgaveHendelse, oppgave: Oppgave) =
        OppgaveOpprettetHendelse(
            behandlingReferanse = behandlingReferanse,
            tidspunkt = hendelse.sendtTid,
            mottattTidspunkt = hendelse.mottattTidspunkt,
            avklaringsbehovKode = oppgave.avklaringsbehov,
            enhet = hendelse.enhet,
            reservertAv = hendelse.reservertAv,
        )

    private fun reservertHendelse(behandlingReferanse: UUID, hendelse: OppgaveHendelse, oppgave: Oppgave) =
        OppgaveReservertHendelse(
            behandlingReferanse = behandlingReferanse,
            tidspunkt = hendelse.sendtTid,
            mottattTidspunkt = hendelse.mottattTidspunkt,
            avklaringsbehovKode = oppgave.avklaringsbehov,
            reservertAv = hendelse.reservertAv.orEmpty(),
            enhet = hendelse.enhet,
        )

    private fun lukketHendelse(behandlingReferanse: UUID, hendelse: OppgaveHendelse, oppgave: Oppgave) =
        OppgaveLukketHendelse(
            behandlingReferanse = behandlingReferanse,
            tidspunkt = hendelse.sendtTid,
            mottattTidspunkt = hendelse.mottattTidspunkt,
            avklaringsbehovKode = oppgave.avklaringsbehov,
        )

    private fun avreservertHendelse(behandlingReferanse: UUID, hendelse: OppgaveHendelse, oppgave: Oppgave) =
        OppgaveAvreservertHendelse(
            behandlingReferanse = behandlingReferanse,
            tidspunkt = hendelse.sendtTid,
            mottattTidspunkt = hendelse.mottattTidspunkt,
            avklaringsbehovKode = oppgave.avklaringsbehov,
        )

    private fun oppdatertHendelse(behandlingReferanse: UUID, hendelse: OppgaveHendelse, oppgave: Oppgave) =
        OppgaveOppdatertHendelse(
            behandlingReferanse = behandlingReferanse,
            tidspunkt = hendelse.sendtTid,
            mottattTidspunkt = hendelse.mottattTidspunkt,
            avklaringsbehovKode = oppgave.avklaringsbehov,
            reservertAv = hendelse.reservertAv,
            enhet = hendelse.enhet,
        )

    private fun byggSnapshots(hendelser: List<SakstatistikkHendelse>): List<SakstatistikkSnapshot> = hendelser
        .runningFold(SakstatistikkState()) { state, hendelse ->
            state.applyHendelse(hendelse)
        }
        .drop(1)
        .zip(hendelser) { state, hendelse ->
            SakstatistikkSnapshot(
                tidspunkt = hendelse.tidspunkt,
                behandlingReferanse = hendelse.behandlingReferanse,
                status = state.status,
                avklaringsbehov = state.avklaringsbehov?.verdi,
                saksbehandler = state.saksbehandler,
                enhet = state.enhet,
                kilde = hendelse.kilde,
            )
        }
}

data class SakstatistikkSnapshot(
    val tidspunkt: LocalDateTime,
    val behandlingReferanse: UUID,
    val status: String?,
    val avklaringsbehov: String?,
    val saksbehandler: String?,
    val enhet: String?,
    val kilde: Kilde,
)
