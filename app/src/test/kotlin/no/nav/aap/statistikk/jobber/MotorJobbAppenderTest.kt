package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import no.nav.aap.statistikk.provider
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.builders.opprettTestHendelse
import no.nav.aap.statistikk.testutils.fakes.FakeBQYtelseRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource

class MotorJobbAppenderTest {
    @Test
    fun `jobber for lagrede behandlinger bruker sakens database-id`(@Postgres dataSource: DataSource) {
        opprettTestHendelse(dataSource, UUID.randomUUID(), Saksnummer("TEST_4Y7KNC0"))
        val (behandlingId, sakId) =
            opprettTestHendelse(dataSource, UUID.randomUUID(), Saksnummer("TEST_4Y7KNC1"))
        val jobbAppender = MotorJobbAppender()

        dataSource.transaction { connection ->
            val provider = connection.provider()
            jobbAppender.leggTilLagreSakTilBigQueryJobb(provider, behandlingId)
            jobbAppender.leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
                provider,
                behandlingId,
                LagreAvsluttetBehandlingTilBigQueryJobb(FakeBQYtelseRepository()),
            )
            jobbAppender.leggTilResendSakstatistikkJobb(provider, behandlingId)
        }

        val nøkler = dataSource.transaction { connection ->
            connection.queryList("SELECT sak_id FROM jobb ORDER BY id") {
                setRowMapper { it.getLong("sak_id") }
            }
        }
        assertThat(nøkler).containsExactly(sakId, sakId, sakId)
    }
}
