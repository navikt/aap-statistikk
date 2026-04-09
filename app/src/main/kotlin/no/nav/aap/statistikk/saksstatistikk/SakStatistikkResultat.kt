package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.behandling.BehandlingId

sealed class SakStatistikkResultat {
    data object OK : SakStatistikkResultat()
    data class ManglerEnhet(
        val behandlingId: BehandlingId,
        val avklaringsbehovKode: String?,
    ) : SakStatistikkResultat()
}