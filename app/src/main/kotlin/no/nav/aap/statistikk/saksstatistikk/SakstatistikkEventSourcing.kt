package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.oppgave.HendelseType
import no.nav.aap.statistikk.oppgave.Oppgave
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
                when (hendelse.hendelse) {
                    HendelseType.OPPRETTET -> {
                        oppgave.behandlingReferanse?.let {
                            OppgaveOpprettetHendelse(
                                behandlingReferanse = it.referanse,
                                tidspunkt = hendelse.opprettetTidspunkt,
                                avklaringsbehovKode = oppgave.avklaringsbehov,
                                enhet = hendelse.enhet,
                                reservertAv = hendelse.reservertAv
                            )
                        }
                    }

                    HendelseType.RESERVERT -> {
                        oppgave.behandlingReferanse?.let {
                            OppgaveReservertHendelse(
                                behandlingReferanse = it.referanse,
                                tidspunkt = hendelse.endretTidspunkt ?: hendelse.mottattTidspunkt,
                                avklaringsbehovKode = oppgave.avklaringsbehov,
                                reservertAv = hendelse.reservertAv ?: "",
                                enhet = hendelse.enhet
                            )
                        }
                    }

                    HendelseType.LUKKET -> {
                        oppgave.behandlingReferanse?.let {
                            OppgaveLukketHendelse(
                                behandlingReferanse = it.referanse,
                                tidspunkt = hendelse.endretTidspunkt ?: hendelse.mottattTidspunkt,
                                avklaringsbehovKode = oppgave.avklaringsbehov
                            )
                        }
                    }

                    HendelseType.AVRESERVERT -> {
                        oppgave.behandlingReferanse?.let {
                            OppgaveAvreservertHendelse(
                                behandlingReferanse = it.referanse,
                                tidspunkt = hendelse.endretTidspunkt ?: hendelse.mottattTidspunkt,
                                avklaringsbehovKode = oppgave.avklaringsbehov,
                            )
                        }
                    }

                    HendelseType.OPPDATERT -> {
                        oppgave.behandlingReferanse?.let {
                            OppgaveOppdatertHendelse(
                                behandlingReferanse = it.referanse,
                                tidspunkt = hendelse.endretTidspunkt ?: hendelse.mottattTidspunkt,
                                avklaringsbehovKode = oppgave.avklaringsbehov,
                                reservertAv = hendelse.reservertAv,
                                enhet = hendelse.enhet
                            )
                        }
                    }
                }
            }
        }
    }

    private fun byggSnapshots(hendelser: List<SakstatistikkHendelse>): List<SakstatistikkSnapshot> {
        val snapshots = mutableListOf<SakstatistikkSnapshot>()
        var state = SakstatistikkState()

        for (hendelse in hendelser) {
            state = state.applyHendelse(hendelse)
            snapshots.add(
                SakstatistikkSnapshot(
                    tidspunkt = hendelse.tidspunkt,
                    behandlingReferanse = hendelse.behandlingReferanse,
                    status = state.status,
                    avklaringsbehov = state.avklaringsbehov,
                    saksbehandler = state.saksbehandler,
                    enhet = state.enhet
                )
            )
        }

        return snapshots
    }
}

data class SakstatistikkSnapshot(
    val tidspunkt: java.time.LocalDateTime,
    val behandlingReferanse: UUID,
    val status: String?,
    val avklaringsbehov: String?,
    val saksbehandler: String?,
    val enhet: String?
)

data class SakstatistikkState(
    val status: String? = null,
    val avklaringsbehov: String? = null,
    val saksbehandler: String? = null,
    val enhet: String? = null
) {
    fun applyHendelse(hendelse: SakstatistikkHendelse): SakstatistikkState {
        return when (hendelse) {
            is BehandlingsflytHendelse -> {
                val nySaksbehandler = if (hendelse.avklaringsbehov == avklaringsbehov) {
                    // Samme avklaringsbehov: bruk sisteSaksbehandler
                    hendelse.sisteSaksbehandlerPåBehandling
                } else if (avklaringsbehov == null && hendelse.sisteLøsteAvklaringsbehov == null) {
                    // Første avklaringsbehov (ingen tidligere løst): bruk sisteSaksbehandler som fallback
                    hendelse.sisteSaksbehandlerPåBehandling
                } else {
                    // Nytt avklaringsbehov (overgang fra tidligere): nullstill
                    null
                }
                val nyEnhet = if (hendelse.avklaringsbehov == avklaringsbehov) {
                    enhet
                } else {
                    null
                }
                copy(
                    status = hendelse.status,
                    avklaringsbehov = hendelse.avklaringsbehov,
                    saksbehandler = nySaksbehandler,
                    enhet = nyEnhet
                )
            }

            is OppgaveOpprettetHendelse -> {
                if (hendelse.avklaringsbehovKode == avklaringsbehov) {
                    copy(
                        enhet = hendelse.enhet,
                        saksbehandler = hendelse.reservertAv
                    )
                } else {
                    this
                }
            }

            is OppgaveReservertHendelse -> {
                if (hendelse.avklaringsbehovKode == avklaringsbehov) {
                    copy(
                        saksbehandler = hendelse.reservertAv,
                        enhet = hendelse.enhet
                    )
                } else {
                    this
                }
            }

            is OppgaveLukketHendelse -> {
                if (hendelse.avklaringsbehovKode == avklaringsbehov) {
                    copy(
                        saksbehandler = null,
                        enhet = null
                    )
                } else {
                    this
                }
            }

            is OppgaveAvreservertHendelse -> {
                if (hendelse.avklaringsbehovKode == avklaringsbehov) {
                    copy(saksbehandler = null)
                } else {
                    this
                }
            }

            is OppgaveOppdatertHendelse -> {
                if (hendelse.avklaringsbehovKode == avklaringsbehov) {
                    copy(
                        saksbehandler = hendelse.reservertAv,
                        enhet = hendelse.enhet
                    )
                } else {
                    this
                }
            }
        }
    }
}
