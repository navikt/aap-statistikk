package no.nav.aap.statistikk.jobber

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import no.nav.aap.statistikk.provider
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.saksstatistikk.LagreSakinfoPayload
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.builders.opprettTestHendelse
import no.nav.aap.statistikk.testutils.fakes.FakeBQYtelseRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

class MotorJobbAppenderTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `lagrer hele sakinfo-payloaden og planlegger neste kjøring`(
        medCutoff: Boolean,
        @Postgres dataSource: DataSource,
    ) {
        val (behandlingId, _) = opprettTestHendelse(dataSource, UUID.randomUUID(), Saksnummer("TEST_4Y7KNC2"))
        val payload = LagreSakinfoPayload(
            behandlingId = behandlingId,
            avklaringsbehovKode = Definisjon.AVKLAR_SYKDOM.kode.name,
            retryCount = 2,
            triggerKilde = "retry(oppgave)",
            cutoffTidspunkt = if (medCutoff) LocalDateTime.of(2026, 11, 19, 10, 0) else null,
        )
        val delayInSeconds = 120L
        val førLagring = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

        dataSource.transaction { connection ->
            MotorJobbAppender().leggTilLagreSakTilBigQueryJobb(
                connection.provider(),
                payload,
                delayInSeconds = delayInSeconds,
            )
        }

        val (lagretPayload, nesteKjøring) = dataSource.transaction { connection ->
            connection.queryList("SELECT payload, neste_kjoring FROM jobb") {
                setRowMapper {
                    DefaultJsonMapper.fromJson<LagreSakinfoPayload>(it.getString("payload")) to
                        it.getLocalDateTime("neste_kjoring")
                }
            }.single()
        }

        assertThat(lagretPayload).isEqualTo(payload)
        assertThat(nesteKjøring).isBetween(
            førLagring.plusSeconds(delayInSeconds),
            LocalDateTime.now().plusSeconds(delayInSeconds + 1),
        )
    }

    @Test
    fun `jobber for lagrede behandlinger bruker sakens database-id`(@Postgres dataSource: DataSource) {
        opprettTestHendelse(dataSource, UUID.randomUUID(), Saksnummer("TEST_4Y7KNC0"))
        val (behandlingId, sakId) =
            opprettTestHendelse(dataSource, UUID.randomUUID(), Saksnummer("TEST_4Y7KNC1"))
        val jobbAppender = MotorJobbAppender()

        dataSource.transaction { connection ->
            val provider = connection.provider()
            jobbAppender.leggTilLagreSakTilBigQueryJobb(provider, LagreSakinfoPayload(behandlingId))
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
