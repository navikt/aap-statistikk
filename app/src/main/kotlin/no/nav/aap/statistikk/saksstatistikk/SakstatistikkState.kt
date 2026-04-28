package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon

data class SakstatistikkState(
    val status: String? = null,
    val avklaringsbehov: Definisjon? = null,
    val saksbehandler: String? = null,
    val enhet: String? = null
) {
    fun applyHendelse(hendelse: SakstatistikkHendelse): SakstatistikkState {
        return when (hendelse) {
            is BehandlingsflytHendelse -> applyBehandlingsflytHendelse(hendelse)
            is OppgaveOpprettetHendelse -> applyOppgaveOpprettetHendelse(hendelse)
            is OppgaveReservertHendelse -> applyOppgaveReservertHendelse(hendelse)
            is OppgaveLukketHendelse -> applyOppgaveLukketHendelse(hendelse)
            is OppgaveAvreservertHendelse -> applyOppgaveAvreservertHendelse(hendelse)
            is OppgaveOppdatertHendelse -> applyOppgaveOppdatertHendelse(hendelse)
        }
    }

    private fun applyBehandlingsflytHendelse(hendelse: BehandlingsflytHendelse): SakstatistikkState {
        val nySaksbehandler = when {
            // Avklaringsbehovet er uendret: behold eksisterende reservasjon fra oppgave.
            // Faller tilbake på sisteSaksbehandlerPåBehandling kun hvis ingen oppgave har reservert ennå.
            hendelse.avklaringsbehov == avklaringsbehov -> saksbehandler
                ?: hendelse.sisteSaksbehandlerPåBehandling

            avklaringsbehov == null && hendelse.sisteLøsteAvklaringsbehov == null -> hendelse.sisteSaksbehandlerPåBehandling
            else -> null
        }
        val avklaringsbehovÅBruke =
            hendelse.avklaringsbehov?.let {
                if (hendelse.avklaringsbehov.erVentebehov() && hendelse.sisteLøsteAvklaringsbehov != null && !hendelse.sisteLøsteAvklaringsbehov.erVentebehov()
                ) hendelse.sisteLøsteAvklaringsbehov else hendelse.avklaringsbehov
            }
        val nyEnhet = if (avklaringsbehovÅBruke == avklaringsbehov) enhet else null
        return copy(
            status = hendelse.status,
            avklaringsbehov = hendelse.avklaringsbehov,
            saksbehandler = nySaksbehandler,
            enhet = nyEnhet
        )
    }

    private fun applyOppgaveOpprettetHendelse(hendelse: OppgaveOpprettetHendelse): SakstatistikkState {
        return if (hendelse.avklaringsbehovKode == avklaringsbehov?.kode?.name) {
            copy(enhet = hendelse.enhet, saksbehandler = hendelse.reservertAv)
        } else {
            this
        }
    }

    private fun applyOppgaveReservertHendelse(hendelse: OppgaveReservertHendelse): SakstatistikkState {
        return if (hendelse.avklaringsbehovKode == avklaringsbehov?.kode?.name) {
            copy(saksbehandler = hendelse.reservertAv, enhet = hendelse.enhet)
        } else {
            this
        }
    }

    private fun applyOppgaveLukketHendelse(hendelse: OppgaveLukketHendelse): SakstatistikkState {
        return if (hendelse.avklaringsbehovKode == avklaringsbehov?.kode?.name) {
            copy(saksbehandler = null, enhet = null)
        } else {
            this
        }
    }

    private fun applyOppgaveAvreservertHendelse(hendelse: OppgaveAvreservertHendelse): SakstatistikkState {
        return if (hendelse.avklaringsbehovKode == avklaringsbehov?.kode?.name) {
            copy(saksbehandler = null)
        } else {
            this
        }
    }

    private fun applyOppgaveOppdatertHendelse(hendelse: OppgaveOppdatertHendelse): SakstatistikkState {
        return if (hendelse.avklaringsbehovKode == avklaringsbehov?.kode?.name) {
            copy(saksbehandler = hendelse.reservertAv, enhet = hendelse.enhet)
        } else {
            this
        }
    }
}