package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.forberedDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class RettighetstypeperiodeRepositoryTest {
    @Test
    fun `lagre og hent igjen`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val now = LocalDate.now()
        dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        val rettighetstypePerioder = listOf(
            RettighetstypePeriode(
                fraDato = now.minusWeeks(2),
                tilDato = now,
                rettighetstype = RettighetsType.STUDENT
            ),
            RettighetstypePeriode(
                fraDato = now.minusWeeks(4),
                tilDato = now.minusWeeks(2).minusDays(1),
                rettighetstype = RettighetsType.STUDENT
            )
        )
        dataSource.transaction {
            RettighetstypeperiodeRepository(it).lagre(
                behandlingReferanse,
                rettighetstypePerioder
            )
        }

        val res =
            dataSource.transaction { RettighetstypeperiodeRepository(it).hent(behandlingReferanse) }

        assertThat(res).hasSize(2)
        assertThat(res).isEqualTo(rettighetstypePerioder)
    }

    @Test
    fun `skal overskrive eksisterende`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val now = LocalDate.now()
        dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        val initialPerioder = listOf(
            RettighetstypePeriode(
                fraDato = now.minusMonths(3),
                tilDato = now.minusMonths(2),
                rettighetstype = RettighetsType.BISTANDSBEHOV
            )
        )
        dataSource.transaction {
            RettighetstypeperiodeRepository(it).lagre(
                behandlingReferanse,
                initialPerioder
            )
        }

        val oppdatertedPerioder = listOf(
            RettighetstypePeriode(
                fraDato = now.minusWeeks(6),
                tilDato = now.minusWeeks(1),
                rettighetstype = RettighetsType.ARBEIDSSØKER
            ),
            RettighetstypePeriode(
                fraDato = now.minusWeeks(12),
                tilDato = now.minusWeeks(6).minusDays(1),
                rettighetstype = RettighetsType.STUDENT
            )
        )
        dataSource.transaction {
            RettighetstypeperiodeRepository(it).lagre(
                behandlingReferanse,
                oppdatertedPerioder
            )
        }

        val res = dataSource.transaction {
            RettighetstypeperiodeRepository(it).hent(behandlingReferanse)
        }

        assertThat(res).hasSize(2)
        assertThat(res).isEqualTo(oppdatertedPerioder)

    }

    @Test
    fun `ikke lagre om input er tom list`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        dataSource.transaction {
            RettighetstypeperiodeRepository(it).lagre(behandlingReferanse, emptyList())
        }

        val res =
            dataSource.transaction { RettighetstypeperiodeRepository(it).hent(behandlingReferanse) }

        assertThat(res).isEmpty()
    }
}