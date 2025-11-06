package no.nav.aap.statistikk.meldekort

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.opprettTestBehandling
import no.nav.aap.statistikk.testutils.opprettTestPerson
import no.nav.aap.statistikk.testutils.opprettTestSak
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource
import kotlin.test.assertEquals

class MeldekortRepositoryTest {

    @Test
    fun `kan lagre samme meldekort to ganger`(@Postgres dataSource: DataSource) {
        val behandlingRef = UUID.randomUUID()
        val person = opprettTestPerson(dataSource, "123456789")
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

        assertDoesNotThrow {
            dataSource.transaction {
                MeldekortRepository(it).lagre(
                    behandlingId = behandling.id!!,
                    meldekort = meldekort
                )
            }
        }

        val uthentet = dataSource.transaction {
            MeldekortRepository(it).hentMeldekort(
                behandlingId = behandling.id!!
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
                            timerArbeidet = BigDecimal("20")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `Lagre og hente ut igjen meldekort data`(@Postgres dataSource: DataSource) {
        val behandlingRef = UUID.randomUUID()
        val person = opprettTestPerson(dataSource, "123456789")
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
                        timerArbeidet = BigDecimal("20")
                    )
                )
            )
        )


        dataSource.transaction {
            MeldekortRepository(it).lagre(
                behandlingId = behandling.id!!,
                meldekort = meldekort
            )
        }

        val uthentet = dataSource.transaction {
            MeldekortRepository(it).hentMeldekort(
                behandlingId = behandling.id!!
            )
        }

        assertThat(meldekort.size).isEqualTo(uthentet.size)
        assertEquals(uthentet[0].journalpostId, meldekort[0].journalpostId)
        assertThat(uthentet).isEqualTo(meldekort)
    }
}