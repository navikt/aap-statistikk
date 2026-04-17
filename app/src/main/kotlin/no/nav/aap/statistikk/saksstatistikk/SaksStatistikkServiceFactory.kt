package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.hendelser.BehandlingService

internal fun lagSaksStatistikkService(
    repositoryProvider: RepositoryProvider,
    gatewayProvider: GatewayProvider,
): SaksStatistikkService {
    val behandlingService = BehandlingService(repositoryProvider, gatewayProvider)
    return SaksStatistikkService(
        behandlingService = behandlingService,
        sakstatistikkRepository = repositoryProvider.provide(),
        bqBehandlingMapper = BQBehandlingMapper(
            behandlingService = behandlingService,
            rettighetstypeperiodeRepository = repositoryProvider.provide(),
            oppgaveRepository = repositoryProvider.provide(),
            sakstatistikkEventSourcing = SakstatistikkEventSourcing(),
        ),
    )
}
