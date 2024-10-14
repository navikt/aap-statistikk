package no.nav.aap.statistikk.person

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.testutils.Postgres
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.sql.DataSource

class PersonRepositoryTest {
    @Test
    fun `sett inn og hent ut person`(@Postgres dataSource: DataSource) {
        val ident = "13021913"
        dataSource.transaction {
            PersonRepository(it).lagrePerson(
                Person(
                    ident = ident,
                )
            )
        }

        val uthentet = dataSource.transaction { PersonRepository(it).hentPerson(ident = ident) }

        assertThat(uthentet?.ident).isEqualTo(
            ident
        )
        assertThat(uthentet?.id).isNotNull()
    }
}