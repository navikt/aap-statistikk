package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.Factory
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.testutils.FakeAvsluttetBehandlingDTORepository
import no.nav.aap.statistikk.testutils.FakeBQRepository
import no.nav.aap.statistikk.testutils.FakeBeregningsgrunnlagRepository
import no.nav.aap.statistikk.testutils.FakeTilkjentYtelseRepository
import no.nav.aap.statistikk.testutils.FakeVilkårsResultatRepository
import no.nav.aap.statistikk.testutils.avsluttetBehandlingDTO
import no.nav.aap.statistikk.testutils.noOpTransactionExecutor
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID


class LagreAvsluttetBehandlingPostgresJobbTest {
    @Test
    fun `lagre avsluttet behandling-jobb`() {
        val bQRepository = FakeBQRepository()
        val fakeTilkjentYtelseRepository = FakeTilkjentYtelseRepository()
        val fakeBeregningsgrunnlagRepository = FakeBeregningsgrunnlagRepository()
        val fakeVilkårsResultatRepository = FakeVilkårsResultatRepository()

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
            bqRepository = bQRepository
        )
        val avsluttetBehandlingRepository = FakeAvsluttetBehandlingDTORepository()
        val behandlingReferanse = UUID.randomUUID()
        val saksnummer = "4LFK2S0"
        val id = avsluttetBehandlingRepository.lagre(
            avsluttetBehandlingDTO(behandlingReferanse, saksnummer)
        )

        assertThat(id).isEqualTo(0L)

        val jobbutfører = LagreAvsluttetBehandlingPostgresJobbUtfører(
            avsluttetBehandlingService,
            avsluttetBehandlingRepository
        )

        jobbutfører.utfør(
            JobbInput(LagreAvsluttetBehandlingJobbKonstruktør(bQRepository)).medParameter(
                "id",
                "0"
            )
        )

        assertThat(bQRepository.beregningsgrunnlag).hasSize(1)
        assertThat(bQRepository.tilkjentYtelse).hasSize(1)
        assertThat(bQRepository.vilkårsresultater).hasSize(1)
        assertThat(fakeBeregningsgrunnlagRepository.grunnlag.first()).extracting(
            { it.behandlingsReferanse },
            { it.value.er6GBegrenset() }
        ).containsExactly(behandlingReferanse, false)
    }
}