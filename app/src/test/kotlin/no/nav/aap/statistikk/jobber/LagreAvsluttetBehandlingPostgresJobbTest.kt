package no.nav.aap.statistikk.jobber

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.api_kontrakt.BehandlingStatus
import no.nav.aap.statistikk.api_kontrakt.SakStatus
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.testutils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*


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
        fakeBehandlingRepository.opprettBehandling(
            Behandling(
                referanse = randomUUID,
                sak = Sak(
                    id = 0,
                    saksnummer = saksnummer,
                    person = Person(
                        ident = "123",
                        id = 0
                    ),
                    sistOppdatert = LocalDateTime.now(),
                    sakStatus = SakStatus.UTREDES
                ),
                typeBehandling = TypeBehandling.Førstegangsbehandling,
                opprettetTid = LocalDateTime.now(),
                mottattTid = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                versjon = Versjon(verdi = "123"),
                status = BehandlingStatus.UTREDES
            )
        )


        val avsluttetBehandlingService = AvsluttetBehandlingService(
            transactionExecutor = noOpTransactionExecutor,
            tilkjentYtelseRepositoryFactory = { fakeTilkjentYtelseRepository },
            beregningsgrunnlagRepositoryFactory = { fakeBeregningsgrunnlagRepository },
            vilkårsResultatRepositoryFactory = { fakeVilkårsResultatRepository },
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

        // ASSERT
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