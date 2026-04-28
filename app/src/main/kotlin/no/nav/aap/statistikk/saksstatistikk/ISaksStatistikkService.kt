package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.statistikk.behandling.BehandlingId

interface ISaksStatistikkService {
    fun lagreSakInfoTilBigquery(
        behandlingId: BehandlingId,
        lagreUtenEnhet: Boolean = false
    ): SakStatistikkResultat

    fun lagreMedStoredBQBehandling(
        behandlingId: BehandlingId,
        storedBQBehandling: BQBehandling,
        avklaringsbehovKode: Definisjon?,
    ): SakStatistikkResultat
}