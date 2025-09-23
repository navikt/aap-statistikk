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
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.sak.SakService
import no.nav.aap.statistikk.sak.tilSaksnummer
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.FakePdlClient
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.avsluttetBehandlingDTO
import no.nav.aap.statistikk.testutils.behandlingHendelse
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
                ProduksjonsstyringRepository(it).alderLukkedeBehandlinger(
                    enheter = listOf(),
                    alderFra = nå
                )
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
            val skjermingService = SkjermingService(FakePdlClient())
            val meterRegistry = SimpleMeterRegistry()
            val hendelsesService = HendelsesService(
                sakService = SakService(SakRepositoryImpl(conn)),
                avsluttetBehandlingService = AvsluttetBehandlingService(
                    tilkjentYtelseRepository = TilkjentYtelseRepository(conn),
                    beregningsgrunnlagRepository = BeregningsgrunnlagRepository(conn),
                    vilkårsResultatRepository = VilkårsresultatRepository(conn),
                    diagnoseRepository = DiagnoseRepositoryImpl(conn),
                    behandlingRepository = BehandlingRepository(conn),
                    skjermingService = skjermingService,
                    meterRegistry = meterRegistry,
                    rettighetstypeperiodeRepository = RettighetstypeperiodeRepository(conn),
                    opprettBigQueryLagringYtelseCallback = { TODO() }
                ),
                personService = PersonService(PersonRepository(conn)),
                behandlingRepository = BehandlingRepository(conn),
                meterRegistry = meterRegistry,
                opprettBigQueryLagringSakStatistikkCallback = { },
                opprettRekjørSakstatistikkCallback = { TODO() },
            )

            val hendelse = behandlingHendelse(
                "1236".tilSaksnummer(),
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