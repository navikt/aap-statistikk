package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.behandling.BehandlingId

class LagreSakinfoTilBigQueryJobbUtfører(private val sakStatistikkService: SaksStatistikkService) :
    JobbUtfører {

    override fun utfør(input: JobbInput) {
        val behandlingId = input.payload<BehandlingId>()
        sakStatistikkService.lagreSakInfoTilBigquery(behandlingId)
    }
}

class LagreSakinfoTilBigQueryJobb(
    private val sakStatistikkService: (DBConnection) -> SaksStatistikkService,
) : Jobb {
    override fun beskrivelse(): String {
        return "Lagrer sakinfo til BigQuery"
    }

    override fun konstruer(connection: DBConnection): JobbUtfører {
        return LagreSakinfoTilBigQueryJobbUtfører(sakStatistikkService(connection))
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
