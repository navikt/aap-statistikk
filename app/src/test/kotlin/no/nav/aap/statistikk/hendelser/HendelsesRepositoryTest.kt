package no.nav.aap.statistikk.hendelser

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.opprettTestPerson
import no.nav.aap.statistikk.testutils.opprettTestSak
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
        val testIdent = "1402202012345"
        val saksnummer = "123"

        val personId = opprettTestPerson(dataSource, testIdent)
        val sakId = opprettTestSak(dataSource, saksnummer, Person(testIdent, id = personId))

        val behandlingReferanse = UUID.randomUUID()
        val behandlingOpprettetTidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

        dataSource.transaction { conn ->
            val repository = HendelsesRepository(
                conn
            )

            repository.lagreHendelse(
                StoppetBehandling(
                    saksnummer = saksnummer,
                    status = "AVS",
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    ident = testIdent,
                    behandlingReferanse = behandlingReferanse,
                    behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                    avklaringsbehov = listOf(),
                    versjon = "ukjent"
                ), sakId
            )
        }

        val hentHendelser = dataSource.transaction { conn ->
            val repository = HendelsesRepository(
                conn
            )
            repository.hentHendelser()
        }
        assertThat(hentHendelser).hasSize(1)
        assertThat(hentHendelser.first()).isEqualTo(
            StoppetBehandling(
                saksnummer = saksnummer,
                status = "AVS",
                behandlingType = TypeBehandling.Førstegangsbehandling,
                ident = testIdent,
                behandlingReferanse = behandlingReferanse,
                behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                avklaringsbehov = listOf(),
                versjon = "ukjent"
            )
        )
    }

    @Test
    fun `sette inn to hendelser i db`(@Postgres dataSource: DataSource) {
        val ident = "21"
        val saksnummer = "123"

        val personId = opprettTestPerson(dataSource, ident)
        val sakId = opprettTestSak(dataSource, saksnummer, Person(ident, id = personId))

        dataSource.transaction { conn ->
            val repository = HendelsesRepository(
                conn
            )

            val behandlingReferanse = UUID.randomUUID()
            val behandlingOpprettetTidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            repository.lagreHendelse(
                StoppetBehandling(
                    saksnummer = saksnummer,
                    status = "AVS",
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    ident = ident,
                    behandlingReferanse = behandlingReferanse,
                    behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                    avklaringsbehov = listOf(),
                    versjon = "ukjent"
                ), sakId
            )


            assertDoesNotThrow {
                repository.lagreHendelse(
                    StoppetBehandling(
                        saksnummer = saksnummer,
                        status = "BEHA",
                        behandlingType = TypeBehandling.Førstegangsbehandling,
                        ident = ident,
                        behandlingReferanse = behandlingReferanse,
                        behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                        avklaringsbehov = listOf(),
                        versjon = "ukjent"
                    ), sakId
                )
            }
        }
    }
}
