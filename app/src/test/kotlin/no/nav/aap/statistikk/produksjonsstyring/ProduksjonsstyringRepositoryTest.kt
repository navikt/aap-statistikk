package no.nav.aap.statistikk.produksjonsstyring

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.hendelser.SaksStatistikkService
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.sak.BigQueryKvitteringRepository
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.testutils.FakePdlClient
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.behandlingHendelse
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class ProduksjonsstyringRepositoryTest {
    @Test
    fun `skal legge i riktige bøtter`(@Postgres dataSource: DataSource) {
        settInnBehandling(dataSource, LocalDateTime.now())
        settInnBehandling(
            dataSource,
            LocalDateTime.now().minusDays(1)
        )
        settInnBehandling(
            dataSource,
            LocalDateTime.now().minusDays(2)
        )
        settInnBehandling(
            dataSource,
            LocalDateTime.now().minusDays(2)
        )

        val res = dataSource.transaction {
            val alderÅpneBehandlinger = ProduksjonsstyringRepository(it).alderÅpneBehandlinger()
            alderÅpneBehandlinger
        }

        assertThat(res).hasSize(3)
        assertThat(res).containsExactly(
            FordelingÅpneBehandlinger(bøtte = 0, antall = 1),
            FordelingÅpneBehandlinger(bøtte = 1, antall = 1),
            FordelingÅpneBehandlinger(bøtte = 2, antall = 2)
        )
    }

    private fun settInnBehandling(dataSource: DataSource, mottattTid: LocalDateTime) =
        dataSource.transaction { conn ->
            val bqRepository = mockk<IBQRepository>(relaxed = true)

            val skjermingService = SkjermingService(FakePdlClient())
            val meterRegistry = SimpleMeterRegistry()
            val hendelsesService = HendelsesService(
                sakRepository = SakRepositoryImpl(conn),
                avsluttetBehandlingService = AvsluttetBehandlingService(
                    tilkjentYtelseRepositoryFactory = TilkjentYtelseRepository(conn),
                    beregningsgrunnlagRepositoryFactory = BeregningsgrunnlagRepository(conn),
                    vilkårsResultatRepositoryFactory = VilkårsresultatRepository(conn),
                    bqRepository = bqRepository,
                    behandlingRepository = BehandlingRepository(conn),
                    skjermingService = skjermingService,
                    avsluttetBehandlingLagretCounter = meterRegistry.avsluttetBehandlingLagret()
                ),
                personRepository = PersonRepository(conn),
                behandlingRepository = BehandlingRepository(conn),
                meterRegistry = meterRegistry,
                sakStatistikkService = SaksStatistikkService(
                    behandlingRepository = BehandlingRepository(conn),
                    bigQueryKvitteringRepository = BigQueryKvitteringRepository(conn),
                    bigQueryRepository = bqRepository,
                    skjermingService = skjermingService,
                )
            )

            val hendelse = behandlingHendelse(
                "123",
                UUID.randomUUID()
            ).copy(mottattTid = mottattTid)

            hendelsesService.prosesserNyHendelse(hendelse)
        }
}