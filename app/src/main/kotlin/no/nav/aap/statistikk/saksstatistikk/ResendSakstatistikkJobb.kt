package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypeperiodeRepository
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.bigquery.IBQSakstatistikkRepository
import no.nav.aap.statistikk.integrasjoner.pdl.PdlClient
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepository
import no.nav.aap.statistikk.sak.BigQueryKvitteringRepository
import no.nav.aap.statistikk.skjerming.SkjermingService
import org.slf4j.LoggerFactory

class ResendSakstatistikkJobbUtfører(
    val sakStatikkService: SaksStatistikkService,
    val sakstatistikkRepository: SakstatistikkRepository
) : JobbUtfører {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val behandlingId = input.payload<Long>().let(::BehandlingId)

        val alleHendelser = sakStatikkService.alleHendelserPåBehandling(behandlingId)
        val ids = sakstatistikkRepository.lagreFlere(alleHendelser)
        log.info("Lagret ${alleHendelser.size} hendelser i sakssakstatistikk-tabell.")
    }
}

class ResendSakstatistikkJobb(
    private val pdlClient: PdlClient,
    private val bigQuerySakstatikkRepository: IBQSakstatistikkRepository,
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        return ResendSakstatistikkJobbUtfører(
            SaksStatistikkService(
                behandlingRepository = BehandlingRepository(connection),
                rettighetstypeperiodeRepository = RettighetstypeperiodeRepository(connection),
                bigQueryKvitteringRepository = BigQueryKvitteringRepository(connection),
                bigQueryRepository = bigQuerySakstatikkRepository,
                skjermingService = SkjermingService(
                    pdlClient = pdlClient
                ),
                oppgaveHendelseRepository = OppgaveHendelseRepository(connection),
                sakstatistikkRepository = SakstatistikkRepositoryImpl(connection),
            ),
            sakstatistikkRepository = SakstatistikkRepositoryImpl(connection),
        )
    }

    override fun type(): String {
        return "statistikk.resendSakstatistikk"
    }

    override fun navn(): String {
        return "Resend saksstatikk"
    }

    override fun beskrivelse(): String {
        return "Resend saksstatikk"
    }

    override val retries: Int
        get() = 1
}