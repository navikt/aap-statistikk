package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.api_kontrakt.BehandlingStatus
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.opprettTestPerson
import no.nav.aap.statistikk.testutils.opprettTestSak
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

class BehandlingRepositoryTest {

    @Test
    fun `lagre to ganger med eksisterende versjon`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789", person)

        val referanse = UUID.randomUUID()
        dataSource.transaction {
            BehandlingRepository(it).lagre(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx")
                )
            )
        }

        val referanse2 = UUID.randomUUID()
        dataSource.transaction {
            BehandlingRepository(it).lagre(
                Behandling(
                    referanse = referanse2,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx")
                )
            )
        }

        dataSource.transaction {
            assertThat(BehandlingRepository(it).hent(referanse)).isNotNull()
            assertThat(BehandlingRepository(it).hent(referanse2)).isNotNull()
        }
    }
}