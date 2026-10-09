package no.nav.aap.statistikk.meldekort

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.builders.opprettTestBehandling
import no.nav.aap.statistikk.testutils.builders.opprettTestPerson
import no.nav.aap.statistikk.testutils.builders.opprettTestSak
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class MeldekortRepositoryTest {

    @Test
    fun `kan lagre samme meldekort to ganger`(@Postgres dataSource: DataSource) {
        val behandlingRef = UUID.randomUUID()
        val person = opprettTestPerson(dataSource, no.nav.aap.statistikk.person.Ident("123456789"))
        val sak = opprettTestSak(dataSource, "123456789".let(::Saksnummer), person)
        val behandling = opprettTestBehandling(dataSource, behandlingRef, sak)
        val meldekort = listOf(
            Meldekort(
                journalpostId = "JP654321",
                arbeidIPeriodeDTO = listOf(
                    ArbeidIPerioder(
                        periodeFom = LocalDate.of(2024, 1, 1),
                        periodeTom = LocalDate.of(2024, 1, 7),
                        timerArbeidet = BigDecimal("20")
                    )
                )
            ),
            Meldekort(
                journalpostId = "JP654321",
                arbeidIPeriodeDTO = listOf(
                    ArbeidIPerioder(
                        periodeFom = LocalDate.of(2024, 1, 1),
                        periodeTom = LocalDate.of(2024, 1, 7),
                        timerArbeidet = BigDecimal("20")
                    )
                )
            )
        )

        val logger = LoggerFactory.getLogger(MeldekortRepository::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply {
            context = logger.loggerContext
            start()
        }
        logger.addAppender(appender)
        try {
            assertDoesNotThrow {
                dataSource.transaction {
                    MeldekortRepository(it).lagre(
                        behandlingId = behandling.id(),
                        meldekort = meldekort
                    )
                }
            }
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }

        assertThat(appender.list).hasSize(1)
        assertThat(appender.list.single().level).isEqualTo(Level.WARN)
        assertThat(appender.list.single().formattedMessage)
            .contains("JP654321", behandling.id().id.toString())

        val uthentet = dataSource.transaction {
            MeldekortRepository(it).hentMeldekort(
                behandlingId = behandling.id()
            )
        }

        assertThat(uthentet).isEqualTo(
            listOf(
                Meldekort(
                    journalpostId = "JP654321",
                    arbeidIPeriodeDTO = listOf(
                        ArbeidIPerioder(
                            periodeFom = LocalDate.of(2024, 1, 1),
                            periodeTom = LocalDate.of(2024, 1, 7),
                            timerArbeidet = BigDecimal("20.0")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `Lagre og hente ut igjen meldekort data`(@Postgres dataSource: DataSource) {
        val behandlingRef = UUID.randomUUID()
        val person = opprettTestPerson(dataSource, no.nav.aap.statistikk.person.Ident("123456789"))
        val sak = opprettTestSak(dataSource, "123456789".let(::Saksnummer), person)
        val behandling = opprettTestBehandling(dataSource, behandlingRef, sak)

        val meldekort = listOf(
            Meldekort(
                journalpostId = "JP123456",
                arbeidIPeriodeDTO = listOf()
            ),
            Meldekort(
                journalpostId = "JP654321",
                arbeidIPeriodeDTO = listOf(
                    ArbeidIPerioder(
                        periodeFom = LocalDate.of(2024, 1, 1),
                        periodeTom = LocalDate.of(2024, 1, 7),
                        timerArbeidet = BigDecimal("7.5")
                    )
                )
            )
        )


        dataSource.transaction {
            MeldekortRepository(it).lagre(
                behandlingId = behandling.id(),
                meldekort = meldekort
            )
        }

        val uthentet = dataSource.transaction {
            MeldekortRepository(it).hentMeldekort(
                behandlingId = behandling.id()
            )
        }

        assertThat(meldekort.size).isEqualTo(uthentet.size)
        assertThat(uthentet[0].journalpostId).isEqualTo(meldekort[0].journalpostId)
        assertThat(uthentet).isEqualTo(meldekort)
    }
}