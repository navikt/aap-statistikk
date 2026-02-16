package no.nav.aap.statistikk.saksstatistikk

data class SakstatistikkState(
    val status: String? = null,
    val avklaringsbehov: String? = null,
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
            hendelse.avklaringsbehov == avklaringsbehov -> hendelse.sisteSaksbehandlerPåBehandling
            avklaringsbehov == null && hendelse.sisteLøsteAvklaringsbehov == null -> hendelse.sisteSaksbehandlerPåBehandling
            else -> null
        }
        val nyEnhet = if (hendelse.avklaringsbehov == avklaringsbehov) enhet else null
        return copy(
            status = hendelse.status,
            avklaringsbehov = hendelse.avklaringsbehov,
            saksbehandler = nySaksbehandler,
            enhet = nyEnhet
        )
    }

    private fun applyOppgaveOpprettetHendelse(hendelse: OppgaveOpprettetHendelse): SakstatistikkState {
        return if (hendelse.avklaringsbehovKode == avklaringsbehov) {
            copy(enhet = hendelse.enhet, saksbehandler = hendelse.reservertAv)
        } else {
            this
        }
    }

    private fun applyOppgaveReservertHendelse(hendelse: OppgaveReservertHendelse): SakstatistikkState {
        return if (hendelse.avklaringsbehovKode == avklaringsbehov) {
            copy(saksbehandler = hendelse.reservertAv, enhet = hendelse.enhet)
        } else {
            this
        }
    }

    private fun applyOppgaveLukketHendelse(hendelse: OppgaveLukketHendelse): SakstatistikkState {
        return if (hendelse.avklaringsbehovKode == avklaringsbehov) {
            copy(saksbehandler = null, enhet = null)
        } else {
            this
        }
    }

    private fun applyOppgaveAvreservertHendelse(hendelse: OppgaveAvreservertHendelse): SakstatistikkState {
        return if (hendelse.avklaringsbehovKode == avklaringsbehov) {
            copy(saksbehandler = null)
        } else {
            this
        }
    }

    private fun applyOppgaveOppdatertHendelse(hendelse: OppgaveOppdatertHendelse): SakstatistikkState {
        return if (hendelse.avklaringsbehovKode == avklaringsbehov) {
            copy(saksbehandler = hendelse.reservertAv, enhet = hendelse.enhet)
        } else {
            this
        }
    }
}