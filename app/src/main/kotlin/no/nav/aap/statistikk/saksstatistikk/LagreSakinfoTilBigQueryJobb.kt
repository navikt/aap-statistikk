package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.defaultGatewayProvider
import no.nav.aap.statistikk.postgresRepositoryRegistry

class LagreSakinfoTilBigQueryJobbUtfører(private val sakStatistikkService: SaksStatistikkService) :
    JobbUtfører {

    override fun utfør(input: JobbInput) {
        val behandlingId = input.payload<BehandlingId>()
        sakStatistikkService.lagreSakInfoTilBigquery(behandlingId)
    }
}

class LagreSakinfoTilBigQueryJobb : Jobb {
    override fun beskrivelse(): String {
        return "Lagrer sakinfo til BigQuery"
    }

    override fun konstruer(connection: DBConnection): JobbUtfører {
        val sakStatistikkService = SaksStatistikkService.konstruer(
            defaultGatewayProvider(),
            postgresRepositoryRegistry.provider(connection)
        )
        return LagreSakinfoTilBigQueryJobbUtfører(sakStatistikkService)
    }

    override fun navn(): String {
        return "lagreSakinfoTilBigQuery"
    }

    override fun type(): String {
        return "statistikk.lagreSakinfoTilBigQueryJobb"
    }

    override fun retries(): Int {
        return 1
    }
}
