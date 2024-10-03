package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.api_kontrakt.BehandlingStatus
import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
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
        val hendelsesService = HendelsesService(
            hendelsesRepository = FakeHendelsesRepository(),
            sakRepository = FakeSakRepository(),
            personRepository = FakePersonRepository(),
            behandlingRepository = FakeBehandlingRepository(),
            bigQueryRepository = bigQueryRepository,
            clock = clock,
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
                avklaringsbehov = listOf()
            )
        )

        assertThat(bigQueryRepository.saker).hasSize(1)
        assertThat(bigQueryRepository.saker.first().saksnummer).isEqualTo("1234")
        assertThat(bigQueryRepository.saker.first().tekniskTid).isEqualTo(
            LocalDateTime.now(clock)
        )
    }
}