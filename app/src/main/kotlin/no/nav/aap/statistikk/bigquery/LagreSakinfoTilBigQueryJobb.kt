package no.nav.aap.statistikk.bigquery

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypeperiodeRepository
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.hendelser.SaksStatistikkService
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.sak.BigQueryKvitteringRepository
import no.nav.aap.statistikk.sak.IBigQueryKvitteringRepository

class LagreSakinfoTilBigQueryJobbUtfører(private val sakStatistikkService: SaksStatistikkService) :
    JobbUtfører {

    override fun utfør(input: JobbInput) {
        val behandlingId = input.payload<Long>()
        sakStatistikkService.lagreSakInfoTilBigquery(behandlingId)
    }
}

class LagreSakinfoTilBigQueryJobb(
    private val bigQueryKvitteringRepository: (DBConnection) -> IBigQueryKvitteringRepository,
    private val behandlingRepositoryFactory: (DBConnection) -> IBehandlingRepository,
    private val bqSakstatikk: IBQSakstatistikkRepository,
    private val skjermingService: SkjermingService,
) : Jobb {
    override fun beskrivelse(): String {
        return "Lagrer sakinfo til BigQuery"
    }

    override fun konstruer(connection: DBConnection): JobbUtfører {
        val sakStatistikkService = SaksStatistikkService(
            behandlingRepository = behandlingRepositoryFactory(connection),
            bigQueryKvitteringRepository = bigQueryKvitteringRepository(connection),
            bigQueryRepository = bqSakstatikk,
            skjermingService = skjermingService,
            rettighetstypeperiodeRepository = RettighetstypeperiodeRepository(connection),
        )
        return LagreSakinfoTilBigQueryJobbUtfører(sakStatistikkService)
    }

    override fun navn(): String {
        return "lagreSakinfoTilBigQuery"
    }

    override fun type(): String {
        return "statistikk.lagreSakinfoTilBigQueryJobb"
    }
}
