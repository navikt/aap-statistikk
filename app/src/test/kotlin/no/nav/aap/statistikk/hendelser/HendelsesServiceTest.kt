package no.nav.aap.statistikk.hendelser

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.statistikk.api_kontrakt.BehandlingStatus
import no.nav.aap.statistikk.api_kontrakt.SakStatus
import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.hendelseLagret
import no.nav.aap.statistikk.testutils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class HendelsesServiceTest {
    @Test
    fun `hendelses-service lagrer i bigquery med korrekt tidspunkt`() {
        val bigQueryRepository = FakeBQRepository()
        val currentInstant = Instant.now()
        val clock = Clock.fixed(currentInstant, ZoneId.of("Europe/Oslo"))
        val behandlingRepository = FakeBehandlingRepository()
        val simpleMeterRegistry = SimpleMeterRegistry()
        val hendelseLagretCounter = simpleMeterRegistry.hendelseLagret()
        val hendelsesService = HendelsesService(
            sakRepository = FakeSakRepository(),
            personRepository = FakePersonRepository(),
            behandlingRepository = behandlingRepository,
            bigQueryRepository = bigQueryRepository,
            bigQueryKvitteringRepository = FakeBigQueryKvitteringRepository(),
            avsluttetBehandlingService = AvsluttetBehandlingService(
                transactionExecutor = noOpTransactionExecutor,
                tilkjentYtelseRepositoryFactory = { FakeTilkjentYtelseRepository() },
                beregningsgrunnlagRepositoryFactory = { FakeBeregningsgrunnlagRepository() },
                vilkårsResultatRepositoryFactory = { FakeVilkårsResultatRepository() },
                bqRepository = bigQueryRepository,
                behandlingRepositoryFactory = { behandlingRepository },
                avsluttetBehandlingLagretCounter = simpleMeterRegistry.avsluttetBehandlingLagret()
            ),
            clock = clock,
            hendelseLagretCounter = hendelseLagretCounter
        )


        hendelsesService.prosesserNyHendelse(
            StoppetBehandling(
                saksnummer = "1234",
                behandlingReferanse = UUID.randomUUID(),
                behandlingOpprettetTidspunkt = LocalDateTime.now(clock),
                status = BehandlingStatus.OPPRETTET,
                behandlingType = TypeBehandling.Revurdering,
                ident = "234",
                versjon = "dsad",
                avklaringsbehov = listOf(),
                mottattTid = LocalDateTime.now().minusDays(1),
                sakStatus = SakStatus.OPPRETTET,
                hendelsesTidspunkt = LocalDateTime.now()
            )
        )

        assertThat(bigQueryRepository.saker).hasSize(1)
        assertThat(bigQueryRepository.saker.first().saksnummer).isEqualTo("1234")
        assertThat(bigQueryRepository.saker.first().tekniskTid).isEqualTo(
            LocalDateTime.now(clock)
        )
        assertThat(hendelseLagretCounter.count()).isEqualTo(1.0)
    }
}