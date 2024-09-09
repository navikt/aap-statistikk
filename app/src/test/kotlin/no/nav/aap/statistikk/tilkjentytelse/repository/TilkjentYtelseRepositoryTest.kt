package no.nav.aap.statistikk.tilkjentytelse.repository

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.Postgres
import no.nav.aap.statistikk.api_kontrakt.MottaStatistikkDTO
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.db.EksistererAlleredeAvbrudd
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class TilkjentYtelseRepositoryTest {
    @Test
    fun `kun én lagret tilkjent ytelse per behandlingsreferanse`(@Postgres dataSource: DataSource) {
        val tilkjentYtelseRepository = TilkjentYtelseRepository(dataSource)

        val behandlingsReferanse = UUID.randomUUID()
        val tilkjentYtelse =
            TilkjentYtelseEntity(
                saksnummer = "ABCDE",
                behandlingsReferanse = behandlingsReferanse,
                perioder = listOf()
            )

        dataSource.transaction { conn ->
            val hendelsesRepository = HendelsesRepository(conn)
            hendelsesRepository.lagreHendelse(
                MottaStatistikkDTO(
                    saksnummer = "ABCDE",
                    behandlingReferanse = behandlingsReferanse,
                    behandlingOpprettetTidspunkt = LocalDateTime.now(),
                    status = "somestatus",
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    ident = "13",
                    avklaringsbehov = listOf()
                )
            )
        }


        tilkjentYtelseRepository.lagreTilkjentYtelse(tilkjentYtelse)

        assertThrows<EksistererAlleredeAvbrudd> {
            tilkjentYtelseRepository.lagreTilkjentYtelse(
                tilkjentYtelse
            )
        }
    }
}