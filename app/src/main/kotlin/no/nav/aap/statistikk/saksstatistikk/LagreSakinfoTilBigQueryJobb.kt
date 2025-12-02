package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProvidersJobbSpesifikasjon
import no.nav.aap.statistikk.behandling.BehandlingId

class LagreSakinfoTilBigQueryJobbUtfører(private val sakStatistikkService: SaksStatistikkService) :
    JobbUtfører {

    override fun utfør(input: JobbInput) {
        val behandlingId = input.payload<BehandlingId>()
        sakStatistikkService.lagreSakInfoTilBigquery(behandlingId)
    }
}

class LagreSakinfoTilBigQueryJobb : ProvidersJobbSpesifikasjon {
    override fun konstruer(
        repositoryProvider: RepositoryProvider,
        gatewayProvider: GatewayProvider
    ): JobbUtfører {
        val sakStatistikkService = SaksStatistikkService.konstruer(
            gatewayProvider,
            repositoryProvider,
        )
        return LagreSakinfoTilBigQueryJobbUtfører(sakStatistikkService)
    }

    override val retries = 1
    override val type: String = "statistikk.lagreSakinfoTilBigQueryJobb"
    override val navn: String = "lagreSakinfoTilBigQuery"

    override val beskrivelse: String = "Lagrer sakinfo til BigQuery"
}
