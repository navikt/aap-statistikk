package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.behandling.TypeBehandling

object Konstanter {
    val interessanteBehandlingstyper = listOf(
        TypeBehandling.Førstegangsbehandling,
        TypeBehandling.Revurdering,
        TypeBehandling.Klage,
        TypeBehandling.Tilbakekreving,
    )
}
