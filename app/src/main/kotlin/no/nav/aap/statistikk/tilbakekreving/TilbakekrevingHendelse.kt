package no.nav.aap.statistikk.tilbakekreving

import no.nav.aap.statistikk.sak.Saksnummer
import java.math.BigDecimal
import java.time.LocalDateTime

enum class TilbakekrevingBehandlingStatus {
    OPPRETTET,
    TIL_BEHANDLING,
    TIL_GODKJENNING,
    TIL_BESLUTTER,
    RETUR_FRA_BESLUTTER,
    AVSLUTTET,
}

data class TilbakekrevingHendelse(
    val saksnummer: Saksnummer,
    val behandlingRef: String,
    val behandlingStatus: TilbakekrevingBehandlingStatus,
    val sakOpprettet: LocalDateTime,
    val totaltFeilutbetaltBeløp: BigDecimal,
    val saksbehandlingURL: String,
    val opprettetTid: LocalDateTime,
)
