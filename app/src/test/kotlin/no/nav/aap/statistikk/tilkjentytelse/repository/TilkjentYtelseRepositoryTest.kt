package no.nav.aap.statistikk.tilkjentytelse.repository

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.sak.tilSaksnummer
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.opprettTestHendelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class TilkjentYtelseRepositoryTest {
    @Test
    fun `kun én lagret tilkjent ytelse per behandlingsreferanse`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        opprettTestHendelse(dataSource, behandlingsReferanse, "123456789".tilSaksnummer())

        val tilkjentYtelse =
            TilkjentYtelseEntity(
                saksnummer = "ABCDE".tilSaksnummer(),
                behandlingsReferanse = behandlingsReferanse,
                perioder = listOf()
            )

        dataSource.transaction { conn ->
            TilkjentYtelseRepository(conn).lagreTilkjentYtelse(tilkjentYtelse)
        }

        dataSource.transaction { conn ->
            assertThrows<Exception> {
                TilkjentYtelseRepository(conn).lagreTilkjentYtelse(
                    tilkjentYtelse
                )
            }
        }
    }

    @Test
    fun `lagre og hente ut tilkjent ytelse`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        val saksnummer = "ABCDE".tilSaksnummer()

        val tilkjentYtelse =
            TilkjentYtelse(
                saksnummer = saksnummer,
                behandlingsReferanse = behandlingsReferanse,
                perioder = listOf(
                    TilkjentYtelsePeriode(
                        fraDato = LocalDate.now().minusYears(1),
                        tilDato = LocalDate.now().plusDays(1),
                        dagsats = 1337.420,
                        gradering = 90.0
                    ),
                    TilkjentYtelsePeriode(
                        fraDato = LocalDate.now().minusYears(3),
                        tilDato = LocalDate.now().minusYears(2),
                        dagsats = 1234.0,
                        gradering = 45.0
                    )
                )
            )

        opprettTestHendelse(dataSource, behandlingsReferanse, saksnummer)

        val id = dataSource.transaction { conn ->
            val tilkjentYtelseRepository = TilkjentYtelseRepository(conn)


            tilkjentYtelseRepository.lagreTilkjentYtelse(
                TilkjentYtelseEntity.fraDomene(
                    tilkjentYtelse
                )
            )
        }

        val uthentet = dataSource.transaction { conn ->
            val tilkjentYtelseRepository = TilkjentYtelseRepository(conn)
            tilkjentYtelseRepository.hentTilkjentYtelse(id.toInt())
        }!!

        assertThat(uthentet.perioder).isEqualTo(tilkjentYtelse.perioder)
        assertThat(uthentet.saksnummer).isEqualTo(tilkjentYtelse.saksnummer)
        assertThat(uthentet.behandlingsReferanse).isEqualTo(tilkjentYtelse.behandlingsReferanse)
    }
}