package no.nav.aap.statistikk.hendelser

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.testutils.Postgres
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
        val behandlingReferanse = UUID.randomUUID()
        val behandlingOpprettetTidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

        dataSource.transaction { conn ->
            val repository = HendelsesRepository(conn, SakRepositoryImpl(conn))

            repository.lagreHendelse(
                StoppetBehandling(
                    saksnummer = "123",
                    status = "AVS",
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    ident = "21",
                    behandlingReferanse = behandlingReferanse,
                    behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                    avklaringsbehov = listOf(),
                    versjon = "ukjent"
                )
            )
        }

        val hentHendelser = dataSource.transaction { conn ->
            val repository = HendelsesRepository(conn, SakRepositoryImpl(conn))
            repository.hentHendelser()
        }
        assertThat(hentHendelser).hasSize(1)
        assertThat(hentHendelser.first()).isEqualTo(
            StoppetBehandling(
                saksnummer = "123",
                status = "AVS",
                behandlingType = TypeBehandling.Førstegangsbehandling,
                ident = "21",
                behandlingReferanse = behandlingReferanse,
                behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                avklaringsbehov = listOf(),
                versjon = "ukjent"
            )
        )
    }

    @Test
    fun `sette inn to hendelser i db`(@Postgres dataSource: DataSource) {
        dataSource.transaction { conn ->
            val repository = HendelsesRepository(conn, SakRepositoryImpl(conn))

            val behandlingReferanse = UUID.randomUUID()
            val behandlingOpprettetTidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            repository.lagreHendelse(
                StoppetBehandling(
                    saksnummer = "123",
                    status = "AVS",
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    ident = "21",
                    behandlingReferanse = behandlingReferanse,
                    behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                    avklaringsbehov = listOf(),
                    versjon = "ukjent"
                )
            )


            assertDoesNotThrow {
                repository.lagreHendelse(
                    StoppetBehandling(
                        saksnummer = "123",
                        status = "BEHA",
                        behandlingType = TypeBehandling.Førstegangsbehandling,
                        ident = "21",
                        behandlingReferanse = behandlingReferanse,
                        behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                        avklaringsbehov = listOf(),
                        versjon = "ukjent"
                    )
                )
            }
        }
    }
}
