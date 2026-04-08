package no.nav.aap.statistikk.jobber.appender

import no.nav.aap.statistikk.behandling.BehandlingId
import java.util.UUID

sealed class StatistikkHendelse {
    data class SakstatistikkSkalLagres(val behandlingId: BehandlingId) : StatistikkHendelse()
    data class YtelsesstatistikkSkalLagres(
        val behandlingId: BehandlingId,
        val behandlingReferanse: UUID
    ) : StatistikkHendelse()
    data class SakstatistikkSkalResendes(val behandlingId: BehandlingId) : StatistikkHendelse()
}
