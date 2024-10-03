package no.nav.aap.statistikk.hendelser

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.api_kontrakt.BehandlingStatus
import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.opprettTestBehandling
import no.nav.aap.statistikk.testutils.opprettTestPerson
import no.nav.aap.statistikk.testutils.opprettTestSak
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.*
import javax.sql.DataSource

class HendelsesRepositoryTest {
    @Test
    fun `sett inn hendelse i db`(@Postgres dataSource: DataSource) {
        val testIdent = "1402202012345"
        val saksnummer = "123"
        val behandlingReferanse = UUID.randomUUID()

        val personId = opprettTestPerson(dataSource, testIdent)
        val sak = opprettTestSak(dataSource, saksnummer, Person(testIdent, id = personId))

        val behandling = opprettTestBehandling(dataSource, behandlingReferanse, sak)
        val behandlingId = behandling.id!!
        val opprettetTidspunkt = behandling.opprettetTid

        dataSource.transaction { conn ->
            val repository = HendelsesRepository(
                conn
            )

            repository.lagreHendelse(
                StoppetBehandling(
                    saksnummer = saksnummer,
                    status = BehandlingStatus.AVSLUTTET,
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    ident = testIdent,
                    behandlingReferanse = behandlingReferanse,
                    behandlingOpprettetTidspunkt = opprettetTidspunkt,
                    avklaringsbehov = listOf(),
                    versjon = "ukjent"
                ), sak.id!!, behandlingId
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
                status = BehandlingStatus.AVSLUTTET,
                behandlingType = TypeBehandling.Førstegangsbehandling,
                ident = testIdent,
                behandlingReferanse = behandlingReferanse,
                behandlingOpprettetTidspunkt = opprettetTidspunkt,
                avklaringsbehov = listOf(),
                versjon = "ukjent"
            )
        )
    }

    @Test
    fun `sette inn to hendelser i db`(@Postgres dataSource: DataSource) {
        val ident = "21"
        val saksnummer = "123"
        val behandlingReferanse = UUID.randomUUID()

        val personId = opprettTestPerson(dataSource, ident)
        val sak = opprettTestSak(dataSource, saksnummer, Person(ident, id = personId))
        val sakId= sak.id!!
        val behandling = opprettTestBehandling(dataSource, behandlingReferanse, sak)
        val behandlingId = behandling.id!!
        val opprettetTidspunkt = behandling.opprettetTid

        dataSource.transaction { conn ->
            val repository = HendelsesRepository(
                conn
            )

            repository.lagreHendelse(
                StoppetBehandling(
                    saksnummer = saksnummer,
                    status = BehandlingStatus.UTREDES,
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    ident = ident,
                    behandlingReferanse = behandlingReferanse,
                    behandlingOpprettetTidspunkt = opprettetTidspunkt,
                    avklaringsbehov = listOf(),
                    versjon = "ukjent"
                ), sakId, behandlingId
            )


            assertDoesNotThrow {
                repository.lagreHendelse(
                    StoppetBehandling(
                        saksnummer = saksnummer,
                        status = BehandlingStatus.AVSLUTTET,
                        behandlingType = TypeBehandling.Førstegangsbehandling,
                        ident = ident,
                        behandlingReferanse = behandlingReferanse,
                        behandlingOpprettetTidspunkt = opprettetTidspunkt,
                        avklaringsbehov = listOf(),
                        versjon = "ukjent2"
                    ), sakId, behandlingId
                )
            }
        }
    }
}
