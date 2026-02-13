package no.nav.aap.statistikk.tilkjentytelse.repository

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.sak.tilSaksnummer
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.opprettTestHendelse
import no.nav.aap.statistikk.tilkjentytelse.Minstesats
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.util.DoubleComparator
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class TilkjentYtelseRepositoryTest {
    @Test
    fun `kun én lagret tilkjent ytelse per behandlingsreferanse - skal overskrive eksisterende`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        opprettTestHendelse(dataSource, behandlingsReferanse, "123456789".tilSaksnummer())

        val tilkjentYtelse =
            TilkjentYtelseEntity(
                saksnummer = "ABCDE".tilSaksnummer(),
                behandlingsReferanse = behandlingsReferanse,
                perioder = listOf(
                    TilkjentYtelsePeriodeEntity(
                        fraDato = LocalDate.now(),
                        tilDato = LocalDate.now().plusDays(1),
                        dagsats = 1000.0,
                        gradering = 100.0,
                        redusertDagsats = 1000.0,
                        utbetalingsdato = LocalDate.now(),
                        antallBarn = 0,
                        barnetilleggSats = 0.0,
                        barnetillegg = 0.0,
                        minstesats = Minstesats.MINSTESATS_OVER_25
                    )
                )
            )

        dataSource.transaction { conn ->
            TilkjentYtelseRepository(conn).lagreTilkjentYtelse(tilkjentYtelse)
        }

        val nyttTilkjentYtelse = tilkjentYtelse.copy(
            perioder = listOf(
                TilkjentYtelsePeriodeEntity(
                    fraDato = LocalDate.now(),
                    tilDato = LocalDate.now().plusDays(1),
                    dagsats = 2000.0,
                    gradering = 100.0,
                    redusertDagsats = 2000.0,
                    utbetalingsdato = LocalDate.now(),
                    antallBarn = 0,
                    barnetilleggSats = 0.0,
                    barnetillegg = 0.0,
                    minstesats = Minstesats.MINSTESATS_OVER_25
                )
            )
        )

        dataSource.transaction { conn ->
            TilkjentYtelseRepository(conn).lagreTilkjentYtelse(nyttTilkjentYtelse)
        }

        val hentetUt = dataSource.transaction { conn ->
            TilkjentYtelseRepository(conn).hentForBehandling(behandlingsReferanse)
        }

        assertThat(hentetUt?.perioder?.first()?.dagsats).isEqualTo(2000.0)
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
                        gradering = 90.0,
                        redusertDagsats = 1337.420 * 0.9,
                        utbetalingsdato = LocalDate.now().minusDays(1),
                        antallBarn = 0,
                        barnetilleggSats = 37.0,
                        barnetillegg = 0.0,
                        minsteSats = Minstesats.MINSTESATS_OVER_25,
                    ),
                    TilkjentYtelsePeriode(
                        fraDato = LocalDate.now().minusYears(3),
                        tilDato = LocalDate.now().minusYears(2),
                        dagsats = 1234.0,
                        gradering = 45.0,
                        redusertDagsats = 1234.0 * 0.45,
                        antallBarn = 1,
                        barnetillegg = 37.0,
                        barnetilleggSats = 37.0,
                        utbetalingsdato = LocalDate.now().minusDays(1),
                        minsteSats = Minstesats.MINSTESATS_UNDER_25,
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
        }

        assertThat(uthentet.perioder)
            .usingRecursiveComparison()
            .withComparatorForType(
                DoubleComparator(0.00001),
                Double::class.javaObjectType
            )
            .isEqualTo(tilkjentYtelse.perioder)

        assertThat(uthentet.saksnummer).isEqualTo(tilkjentYtelse.saksnummer)
        assertThat(uthentet.behandlingsReferanse).isEqualTo(tilkjentYtelse.behandlingsReferanse)
    }
}