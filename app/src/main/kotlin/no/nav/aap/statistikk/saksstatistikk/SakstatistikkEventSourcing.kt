package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.oppgave.HendelseType
import no.nav.aap.statistikk.oppgave.Oppgave
import no.nav.aap.statistikk.oppgave.OppgaveHendelse
import java.time.LocalDateTime
import java.util.*

class SakstatistikkEventSourcing {

    fun byggSakstatistikkHendelser(
        behandling: Behandling,
        oppgaver: List<Oppgave>
    ): List<SakstatistikkSnapshot> {
        val alleSakstatistikkHendelser =
            konverterBehandlingHendelser(behandling) +
                    konverterOppgaveHendelser(oppgaver)

        val sorterteHendelser = alleSakstatistikkHendelser.sortedBy { it.tidspunkt }

        return byggSnapshots(sorterteHendelser)
    }

    private fun konverterBehandlingHendelser(behandling: Behandling): List<SakstatistikkHendelse> {
        return behandling.hendelser.map { hendelse ->
            BehandlingsflytHendelse(
                behandlingReferanse = behandling.referanse,
                tidspunkt = hendelse.hendelsesTidspunkt,
                status = hendelse.status.name,
                avklaringsbehov = hendelse.avklaringsBehov,
                sisteLøsteAvklaringsbehov = hendelse.sisteLøsteAvklaringsbehov,
                sisteSaksbehandlerPåBehandling = hendelse.sisteSaksbehandlerSomLøstebehov
            )
        }
    }

    private fun konverterOppgaveHendelser(oppgaver: List<Oppgave>): List<SakstatistikkHendelse> {
        return oppgaver.flatMap { oppgave ->
            oppgave.hendelser.mapNotNull { hendelse ->
                konverterOppgaveHendelse(oppgave, hendelse)
            }
        }
    }

    private fun konverterOppgaveHendelse(
        oppgave: Oppgave,
        hendelse: OppgaveHendelse
    ): SakstatistikkHendelse? {
        val behandlingReferanse = oppgave.behandlingReferanse ?: return null

        return when (hendelse.hendelse) {
            HendelseType.OPPRETTET -> opprettetHendelse(
                behandlingReferanse.referanse,
                hendelse,
                oppgave
            )

            HendelseType.RESERVERT -> reservertHendelse(
                behandlingReferanse.referanse,
                hendelse,
                oppgave
            )

            HendelseType.LUKKET -> lukketHendelse(behandlingReferanse.referanse, hendelse, oppgave)
            HendelseType.AVRESERVERT -> avreservertHendelse(
                behandlingReferanse.referanse,
                hendelse,
                oppgave
            )

            HendelseType.OPPDATERT -> oppdatertHendelse(
                behandlingReferanse.referanse,
                hendelse,
                oppgave
            )
        }
    }

    private fun opprettetHendelse(
        behandlingReferanse: UUID,
        hendelse: OppgaveHendelse,
        oppgave: Oppgave
    ) = OppgaveOpprettetHendelse(
        behandlingReferanse = behandlingReferanse,
        tidspunkt = hendelse.opprettetTidspunkt,
        avklaringsbehovKode = oppgave.avklaringsbehov,
        enhet = hendelse.enhet,
        reservertAv = hendelse.reservertAv
    )

    private fun reservertHendelse(
        behandlingReferanse: UUID,
        hendelse: OppgaveHendelse,
        oppgave: Oppgave
    ) = OppgaveReservertHendelse(
        behandlingReferanse = behandlingReferanse,
        tidspunkt = hendelse.endretTidspunkt ?: hendelse.mottattTidspunkt,
        avklaringsbehovKode = oppgave.avklaringsbehov,
        reservertAv = hendelse.reservertAv.orEmpty(),
        enhet = hendelse.enhet
    )

    private fun lukketHendelse(
        behandlingReferanse: UUID,
        hendelse: OppgaveHendelse,
        oppgave: Oppgave
    ) = OppgaveLukketHendelse(
        behandlingReferanse = behandlingReferanse,
        tidspunkt = hendelse.endretTidspunkt ?: hendelse.mottattTidspunkt,
        avklaringsbehovKode = oppgave.avklaringsbehov
    )

    private fun avreservertHendelse(
        behandlingReferanse: UUID,
        hendelse: OppgaveHendelse,
        oppgave: Oppgave
    ) = OppgaveAvreservertHendelse(
        behandlingReferanse = behandlingReferanse,
        tidspunkt = hendelse.endretTidspunkt ?: hendelse.mottattTidspunkt,
        avklaringsbehovKode = oppgave.avklaringsbehov,
    )

    private fun oppdatertHendelse(
        behandlingReferanse: UUID,
        hendelse: OppgaveHendelse,
        oppgave: Oppgave
    ) = OppgaveOppdatertHendelse(
        behandlingReferanse = behandlingReferanse,
        tidspunkt = hendelse.endretTidspunkt ?: hendelse.mottattTidspunkt,
        avklaringsbehovKode = oppgave.avklaringsbehov,
        reservertAv = hendelse.reservertAv,
        enhet = hendelse.enhet
    )

    private fun byggSnapshots(hendelser: List<SakstatistikkHendelse>): List<SakstatistikkSnapshot> {
        return hendelser
            .runningFold(SakstatistikkState()) { state, hendelse ->
                state.applyHendelse(hendelse)
            }
            .drop(1)
            .zip(hendelser) { state, hendelse ->
                SakstatistikkSnapshot(
                    tidspunkt = hendelse.tidspunkt,
                    behandlingReferanse = hendelse.behandlingReferanse,
                    status = state.status,
                    avklaringsbehov = state.avklaringsbehov,
                    saksbehandler = state.saksbehandler,
                    enhet = state.enhet
                )
            }
    }
}

data class SakstatistikkSnapshot(
    val tidspunkt: LocalDateTime,
    val behandlingReferanse: UUID,
    val status: String?,
    val avklaringsbehov: String?,
    val saksbehandler: String?,
    val enhet: String?
)

