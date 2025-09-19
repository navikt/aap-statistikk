package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypeperiodeRepository
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.bigquery.IBQSakstatistikkRepository
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepository
import no.nav.aap.statistikk.sak.IBigQueryKvitteringRepository
import no.nav.aap.statistikk.skjerming.SkjermingService

class LagreSakinfoTilBigQueryJobbUtfører(private val sakStatistikkService: SaksStatistikkService) :
    JobbUtfører {

    override fun utfør(input: JobbInput) {
        val behandlingId = input.payload<BehandlingId>()
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
            rettighetstypeperiodeRepository = RettighetstypeperiodeRepository(connection),
            bigQueryKvitteringRepository = bigQueryKvitteringRepository(connection),
            bigQueryRepository = bqSakstatikk,
            skjermingService = skjermingService,
            oppgaveHendelseRepository = OppgaveHendelseRepository(connection),
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
