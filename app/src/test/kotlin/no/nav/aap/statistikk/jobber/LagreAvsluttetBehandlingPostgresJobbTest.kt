package no.nav.aap.statistikk.jobber

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.Factory
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.testutils.*
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID


class LagreAvsluttetBehandlingPostgresJobbTest {
    @Test
    fun `lagre avsluttet behandling-jobb`() {
        val bQRepository = FakeBQRepository()
        val fakeTilkjentYtelseRepository = FakeTilkjentYtelseRepository()
        val fakeBeregningsgrunnlagRepository = FakeBeregningsgrunnlagRepository()
        val fakeVilkårsResultatRepository = FakeVilkårsResultatRepository()

        val fakeBehandlingRepository = FakeBehandlingRepository()
        val randomUUID = UUID.randomUUID()
        val saksnummer = "4LFK2S0"
        fakeBehandlingRepository.lagre(
            Behandling(
                referanse = randomUUID,
                sak = Sak(
                    id = 0,
                    saksnummer = saksnummer,
                    person = Person(
                        ident = "123",
                        id = 0
                    )
                ),
                typeBehandling = TypeBehandling.Førstegangsbehandling,
                opprettetTid = LocalDateTime.now()
            )
        )


        val avsluttetBehandlingService = AvsluttetBehandlingService(
            transactionExecutor = noOpTransactionExecutor,
            tilkjentYtelseRepositoryFactory = object : Factory<FakeTilkjentYtelseRepository> {
                override fun create(dbConnection: DBConnection): FakeTilkjentYtelseRepository {
                    return fakeTilkjentYtelseRepository
                }
            },
            beregningsgrunnlagRepositoryFactory = object : Factory<IBeregningsgrunnlagRepository> {
                override fun create(dbConnection: DBConnection): IBeregningsgrunnlagRepository {
                    return fakeBeregningsgrunnlagRepository
                }
            },
            vilkårsResultatRepositoryFactory = object : Factory<IVilkårsresultatRepository> {
                override fun create(dbConnection: DBConnection): IVilkårsresultatRepository {
                    return fakeVilkårsResultatRepository
                }
            },
            bqRepository = bQRepository,
            behandlingRepositoryFactory = { fakeBehandlingRepository }
        )
        val avsluttetBehandlingRepository = FakeAvsluttetBehandlingDTORepository()
        val id = avsluttetBehandlingRepository.lagre(
            avsluttetBehandlingDTO(randomUUID, saksnummer)
        )

        assertThat(id).isEqualTo(0L)

        val meterRegistry = SimpleMeterRegistry()

        val avsluttetBehandlingLagretCounter = meterRegistry.avsluttetBehandlingLagret()

        val jobbutfører = LagreAvsluttetBehandlingPostgresJobbUtfører(
            avsluttetBehandlingService,
            avsluttetBehandlingRepository,
            avsluttetBehandlingLagretCounter
        )

        // ACT
        jobbutfører.utfør(
            JobbInput(
                LagreAvsluttetBehandlingJobbKonstruktør(
                    bQRepository,
                    avsluttetBehandlingLagretCounter
                )
            ).medParameter(
                "id",
                "0"
            )
        )

        assertThat(bQRepository.beregningsgrunnlag).hasSize(1)
        assertThat(bQRepository.tilkjentYtelse).hasSize(1)
        assertThat(bQRepository.vilkårsresultater).hasSize(1)
        assertThat(fakeBeregningsgrunnlagRepository.grunnlag.first()).extracting(
            { it.behandlingsReferanse },
            { it.value.type().toString() }
        ).containsExactly(randomUUID, "yrkesskade")

        assertThat(avsluttetBehandlingLagretCounter.count()).isEqualTo(1.0)
    }
}