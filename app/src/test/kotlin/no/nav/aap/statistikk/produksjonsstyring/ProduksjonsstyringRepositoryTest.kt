package no.nav.aap.statistikk.produksjonsstyring

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypeperiodeRepository
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.DiagnoseRepositoryImpl
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.hendelser.SaksStatistikkService
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.BigQueryKvitteringRepository
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.testutils.*
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

@Disabled("Disable en liten stund - må gjøre denne deterministisk")
class ProduksjonsstyringRepositoryTest {
    @Test
    fun `skal legge i riktige bøtter for alder på åpne behandlinger`(@Postgres dataSource: DataSource) {
        val nå = LocalDateTime.now().plusYears(1)
        settInnBehandling(dataSource, nå.minusDays(1))
        settInnBehandling(
            dataSource,
            nå.minusDays(3)
        )
        settInnBehandling(
            dataSource,
            nå.minusDays(5)
        )
        settInnBehandling(
            dataSource,
            nå.minusDays(5)
        )

        val res = dataSource.transaction {
            val alderÅpneBehandlinger =
                ProduksjonsstyringRepository(it).alderÅpneBehandlinger(
                    alderFra = nå,
                    behandlingsTyper = listOf(TypeBehandling.Førstegangsbehandling)
                )
            alderÅpneBehandlinger
        }

        assertThat(res).hasSize(3)
        println(res)
        assertThat(res).containsExactlyInAnyOrder(
            BøtteFordeling(bøtte = 2, antall = 1),
            BøtteFordeling(bøtte = 4, antall = 1),
            BøtteFordeling(bøtte = 6, antall = 2)
        )
    }

    @Test
    fun `skal legge i riktige bøtter for alder på lukkede behandlinger`(@Postgres dataSource: DataSource) {
        val nå = LocalDateTime.now().plusYears(1)
        settInnBehandling(dataSource, nå)
        settInnBehandling(
            dataSource,
            nå.minusDays(1),
            åpen = false
        )
        settInnBehandling(
            dataSource,
            nå.minusDays(2), åpen = false
        )
        settInnBehandling(
            dataSource,
            nå.minusDays(2), åpen = false
        )

        val res = dataSource.transaction {
            val alderÅpneBehandlinger =
                ProduksjonsstyringRepository(it).alderLukkedeBehandlinger(enheter = listOf(), alderFra = nå)
            alderÅpneBehandlinger
        }

        assertThat(res).hasSize(2)
        assertThat(res).containsExactlyInAnyOrder(
            BøtteFordeling(bøtte = 2, antall = 1),
            BøtteFordeling(bøtte = 3, antall = 2)
        )
    }

    private fun settInnBehandling(
        dataSource: DataSource,
        mottattTid: LocalDateTime,
        åpen: Boolean = true
    ) =
        dataSource.transaction { conn ->
            val bqRepositoryYtelse = FakeBQYtelseRepository()
            val bqRepositorySak = FakeBQSakRepository()

            val skjermingService = SkjermingService(FakePdlClient())
            val meterRegistry = SimpleMeterRegistry()
            val hendelsesService = HendelsesService(
                sakRepository = SakRepositoryImpl(conn),
                avsluttetBehandlingService = AvsluttetBehandlingService(
                    tilkjentYtelseRepository = TilkjentYtelseRepository(conn),
                    beregningsgrunnlagRepository = BeregningsgrunnlagRepository(conn),
                    vilkårsResultatRepository = VilkårsresultatRepository(conn),
                    diagnoseRepository = DiagnoseRepositoryImpl(conn),
                    bqRepository = bqRepositoryYtelse,
                    behandlingRepository = BehandlingRepository(conn),
                    skjermingService = skjermingService,
                    meterRegistry = meterRegistry,
                    rettighetstypeperiodeRepository = RettighetstypeperiodeRepository(conn),
                ),
                personService = PersonService(PersonRepository(conn)),
                behandlingRepository = BehandlingRepository(conn),
                meterRegistry = meterRegistry,
                sakStatistikkService = SaksStatistikkService(
                    behandlingRepository = BehandlingRepository(conn),
                    bigQueryKvitteringRepository = BigQueryKvitteringRepository(conn),
                    bigQueryRepository = bqRepositorySak,
                    skjermingService = skjermingService,
                    rettighetstypeperiodeRepository = RettighetstypeperiodeRepository(conn),
                )
            )

            val hendelse = behandlingHendelse(
                "1236",
                UUID.randomUUID()
            ).copy(mottattTid = mottattTid)

            hendelsesService.prosesserNyHendelse(hendelse)


            if (!åpen) {
                val avsluttetBehandlingHendelse = hendelse.copy(
                    avsluttetBehandling = avsluttetBehandlingDTO(),
                    behandlingStatus = Status.AVSLUTTET
                )
                hendelsesService.prosesserNyHendelse(avsluttetBehandlingHendelse)
            }
        }
}