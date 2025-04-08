package no.nav.aap.statistikk.sak

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.testutils.Postgres
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
            val ident = "214"
            val person = PersonService(PersonRepository(it)).hentEllerLagrePerson(ident)
            val sakRepositoryImpl = SakRepositoryImpl(it)

            sakRepositoryImpl.settInnSak(
                Sak(
                    saksnummer = saksnummer,
                    person = person,
                    sakStatus = SakStatus.UTREDES,
                    sistOppdatert = LocalDateTime.now(
                        Clock.fixed(
                            now,
                            ZoneId.systemDefault()
                        )
                    )
                )
            )
        }

        val sak = dataSource.transaction {
            val sakRepositoryImpl = SakRepositoryImpl(it)

            sakRepositoryImpl.hentSak(saksnummer)
        }

        assertThat(sak.sakStatus).isEqualTo(
            SakStatus.UTREDES
        )
        assertThat(sak.sistOppdatert).isCloseTo(
            LocalDateTime.ofInstant(
                now,
                ZoneId.systemDefault()
            ), TemporalUnitLessThanOffset(1, ChronoUnit.SECONDS)
        )
    }

    @Test
    fun `oppdatere sak`(@Postgres dataSource: DataSource) {
        val saksnummer = Saksnummer("1234")
        val now = Instant.now()

        // Sette inn
        val fixed = Clock.fixed(
            now,
            ZoneId.systemDefault()
        )
        val sak = dataSource.transaction {
            val ident = "214"
            val person = PersonService(PersonRepository(it)).hentEllerLagrePerson(ident)
            val sakRepositoryImpl = SakRepositoryImpl(it)

            val sak = Sak(
                saksnummer = saksnummer,
                person = person,
                sakStatus = SakStatus.UTREDES,
                sistOppdatert = LocalDateTime.now(fixed).minusDays(1)
            )
            val id = sakRepositoryImpl.settInnSak(
                sak
            )

            sak.copy(id = id)
        }

        // Oppdatere
        dataSource.transaction {
            val sakRepositoryImpl = SakRepositoryImpl(it)
            sakRepositoryImpl.oppdaterSak(
                sak.copy(
                    sakStatus = SakStatus.AVSLUTTET,
                    sistOppdatert = LocalDateTime.now(fixed)
                )
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
                ZoneId.systemDefault()
            ).truncatedTo(ChronoUnit.SECONDS)
        )
        assertThat(uthentet.sakStatus).isEqualTo(
            SakStatus.AVSLUTTET
        )
    }
}