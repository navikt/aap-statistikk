package no.nav.aap.statistikk.sak

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.fakes.FakePdlGateway
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.TemporalUnitLessThanOffset
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

class SakRepositoryImplTest {
    @Test
    fun `sett inn sak og hent ut igjen`(@Postgres dataSource: DataSource) {
        val saksnummer = Saksnummer("1234")
        val now = Instant.now()

        dataSource.transaction {
            val ident = Ident("214")
            val person = PersonService(PersonRepository(it), FakePdlGateway()).hentEllerLagrePerson(ident)
            val sakRepositoryImpl = SakRepositoryImpl(it)

            sakRepositoryImpl.settInnSak(
                Sak(
                    saksnummer = saksnummer,
                    person = person,
                    sistOppdatert = LocalDateTime.now(
                        Clock.fixed(
                            now,
                            ZoneId.systemDefault(),
                        ),
                    ),
                ),
            )
        }

        val sak = dataSource.transaction {
            val sakRepositoryImpl = SakRepositoryImpl(it)

            sakRepositoryImpl.hentSak(saksnummer)
        }

        assertThat(sak.sistOppdatert).isCloseTo(
            LocalDateTime.ofInstant(
                now,
                ZoneId.systemDefault(),
            ),
            TemporalUnitLessThanOffset(1, ChronoUnit.SECONDS),
        )
    }

    @Test
    fun `oppdatere sak`(@Postgres dataSource: DataSource) {
        val saksnummer = Saksnummer("1234")
        val now = Instant.now()

        // Sette inn
        val fixed = Clock.fixed(
            now,
            ZoneId.systemDefault(),
        )
        val sak = dataSource.transaction {
            val ident = Ident("214")
            val person = PersonService(PersonRepository(it), FakePdlGateway()).hentEllerLagrePerson(ident)
            val sakRepositoryImpl = SakRepositoryImpl(it)

            val sak = Sak(
                saksnummer = saksnummer,
                person = person,
                sistOppdatert = LocalDateTime.now(fixed).minusDays(1),
            )
            val id = sakRepositoryImpl.settInnSak(
                sak,
            )

            sak.copy(id = id)
        }

        // Oppdatere
        dataSource.transaction {
            val sakRepositoryImpl = SakRepositoryImpl(it)
            sakRepositoryImpl.oppdaterSak(
                sak.copy(
                    sistOppdatert = LocalDateTime.now(fixed),
                ),
            )
        }

        // Hente ut igjen
        val uthentet = dataSource.transaction {
            val sakRepositoryImpl = SakRepositoryImpl(it)
            sakRepositoryImpl.hentSak(sak.id!!)
        }

        assertThat(uthentet.saksnummer).isEqualTo("1234".tilSaksnummer())
        assertThat(uthentet.sistOppdatert.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(
            LocalDateTime.ofInstant(
                now,
                ZoneId.systemDefault(),
            ).truncatedTo(ChronoUnit.SECONDS),
        )
    }

    @Test
    fun `oppdaterSak med samme status skal ikke lage ny sak_historikk-rad`(@Postgres dataSource: DataSource) {
        val saksnummer = Saksnummer("1234")
        val now = Instant.now()
        val fixed = Clock.fixed(now, ZoneId.systemDefault())

        dataSource.transaction {
            val person = PersonService(PersonRepository(it), FakePdlGateway()).hentEllerLagrePerson(Ident("214"))
            val sakService = SakService(SakRepositoryImpl(it), fixed)
            sakService.hentEllerSettInnSak(person, saksnummer)
        }

        // Kall med samme status
        dataSource.transaction {
            val person = PersonService(PersonRepository(it), FakePdlGateway()).hentEllerLagrePerson(Ident("214"))
            val sakService = SakService(SakRepositoryImpl(it), fixed)
            sakService.hentEllerSettInnSak(person, saksnummer)
        }

        val antallHistorikkRader = dataSource.transaction {
            it.queryFirst("SELECT COUNT(*) AS total FROM sak_historikk") {
                setRowMapper { row -> row.getInt("total") }
            }
        }

        // settInnSak lager 1 rad. Andre kall med samme status skal IKKE lage ny rad.
        assertThat(antallHistorikkRader).isEqualTo(1)
    }
}
