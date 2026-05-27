package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.behandling.BehandlingId
import java.time.LocalDateTime

interface ISaksStatistikkService {
    fun lagreSakInfoTilBigquery(
        behandlingId: BehandlingId,
        cutoffTidspunkt: LocalDateTime? = null,
    ): SakStatistikkResultat
}