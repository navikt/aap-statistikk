package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.Postgres
import no.nav.aap.statistikk.api_kontrakt.MottaStatistikkDTO
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

class HendelsesRepositoryTest {
    @Test
    fun `sett inn hendelse i db`(@Postgres dataSource: DataSource) {
        val repository = HendelsesRepository(dataSource)

        val behandlingReferanse = UUID.randomUUID()
        val behandlingOpprettetTidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        repository.lagreHendelse(
            MottaStatistikkDTO(
                saksnummer = "123",
                status = "AVS",
                behandlingType = TypeBehandling.Førstegangsbehandling,
                ident = "21",
                behandlingReferanse = behandlingReferanse,
                behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                avklaringsbehov = listOf()
            )
        )

        val hentHendelser = repository.hentHendelser()

        assertThat(hentHendelser).hasSize(1)
        assertThat(hentHendelser.first()).isEqualTo(
            MottaStatistikkDTO(
                saksnummer = "123",
                status = "AVS",
                behandlingType = TypeBehandling.Førstegangsbehandling,
                ident = "21",
                behandlingReferanse = behandlingReferanse,
                behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                avklaringsbehov = listOf()
            )
        )
    }

    @Test
    fun `sette inn to hendelser i db`(@Postgres dataSource: DataSource) {
        val repository = HendelsesRepository(dataSource)

        val behandlingReferanse = UUID.randomUUID()
        val behandlingOpprettetTidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        repository.lagreHendelse(
            MottaStatistikkDTO(
                saksnummer = "123",
                status = "AVS",
                behandlingType = TypeBehandling.Førstegangsbehandling,
                ident = "21",
                behandlingReferanse = behandlingReferanse,
                behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                avklaringsbehov = listOf()
            )
        )

        assertDoesNotThrow {
            repository.lagreHendelse(
                MottaStatistikkDTO(
                    saksnummer = "123",
                    status = "BEHA",
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    ident = "21",
                    behandlingReferanse = behandlingReferanse,
                    behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                    avklaringsbehov = listOf()
                )
            )
        }

    }
}
