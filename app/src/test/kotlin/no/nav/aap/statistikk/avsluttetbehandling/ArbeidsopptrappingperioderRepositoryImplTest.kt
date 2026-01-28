package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.type.Periode
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.forberedDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class ArbeidsopptrappingperioderRepositoryImplTest {
    @Test
    fun `lagre og hent igjen`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val now = LocalDate.now()
        val behandlingId = dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        val perioder = listOf(
            Periode(
                fom = now.minusWeeks(4),
                tom = now.minusWeeks(2)
            ),
            Periode(
                fom = now.minusWeeks(2).plusDays(1),
                tom = now
            )
        )

        dataSource.transaction {
            ArbeidsopptrappingperioderRepositoryImpl(it).lagre(behandlingId, perioder)
        }

        val res = dataSource.transaction {
            ArbeidsopptrappingperioderRepositoryImpl(it).hent(behandlingId)
        }

        assertThat(res).hasSize(2)
        assertThat(res).isEqualTo(perioder)
    }

    @Test
    fun `skal overskrive eksisterende perioder ved ny lagring`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val now = LocalDate.now()
        val behandlingId = dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        val initialPerioder = listOf(
            Periode(
                fom = now.minusMonths(3),
                tom = now.minusMonths(2)
            )
        )

        dataSource.transaction {
            ArbeidsopptrappingperioderRepositoryImpl(it).lagre(behandlingId, initialPerioder)
        }

        val oppdatertePerioder = listOf(
            Periode(
                fom = now.minusWeeks(6),
                tom = now.minusWeeks(1)
            ),
            Periode(
                fom = now.minusWeeks(12),
                tom = now.minusWeeks(7)
            )
        )

        dataSource.transaction {
            ArbeidsopptrappingperioderRepositoryImpl(it).lagre(behandlingId, oppdatertePerioder)
        }

        val res = dataSource.transaction {
            ArbeidsopptrappingperioderRepositoryImpl(it).hent(behandlingId)
        }

        assertThat(res).hasSize(2)
        assertThat(res).isEqualTo(oppdatertePerioder)
    }

    @Test
    fun `tom liste sletter eksisterende data`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val now = LocalDate.now()
        val behandlingId = dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        val initialPerioder = listOf(
            Periode(
                fom = now.minusMonths(3),
                tom = now.minusMonths(2)
            ),
            Periode(
                fom = now.minusMonths(2).plusDays(1),
                tom = now.minusMonths(1)
            )
        )

        dataSource.transaction {
            ArbeidsopptrappingperioderRepositoryImpl(it).lagre(behandlingId, initialPerioder)
        }

        dataSource.transaction {
            ArbeidsopptrappingperioderRepositoryImpl(it).lagre(behandlingId, emptyList())
        }

        val res = dataSource.transaction {
            ArbeidsopptrappingperioderRepositoryImpl(it).hent(behandlingId)
        }

        assertThat(res).isEmpty()
    }

    @Test
    fun `hent returnerer null for ikke-eksisterende behandling`(@Postgres dataSource: DataSource) {
        val ikkeEksisterendeBehandlingId = BehandlingId(999999L)

        val res = dataSource.transaction {
            ArbeidsopptrappingperioderRepositoryImpl(it).hent(ikkeEksisterendeBehandlingId)
        }

        assertThat(res).isNull()
    }
}
