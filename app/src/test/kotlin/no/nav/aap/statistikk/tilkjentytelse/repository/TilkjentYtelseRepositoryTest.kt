package no.nav.aap.statistikk.tilkjentytelse.repository

import no.nav.aap.statistikk.Postgres
import no.nav.aap.statistikk.db.EksistererAlleredeAvbrudd
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

class TilkjentYtelseRepositoryTest {
    @Test
    fun `kun én lagret tilkjent ytelse per behandlingsreferanse`(@Postgres dataSource: DataSource) {
        val tilkjentYtelseRepository = TilkjentYtelseRepository(dataSource)

        val behandlingsReferanse = UUID.randomUUID()
        val tilkjentYtelse =
            TilkjentYtelseEntity(saksnummer = "ABCDE", behandlingsReferanse = behandlingsReferanse, perioder = listOf())

        tilkjentYtelseRepository.lagreTilkjentYtelse(tilkjentYtelse)

        org.junit.jupiter.api.assertThrows<EksistererAlleredeAvbrudd> {
            tilkjentYtelseRepository.lagreTilkjentYtelse(
                tilkjentYtelse
            )
        }
    }
}