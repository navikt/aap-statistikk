package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.avsluttetbehandling.DiagnoseMedPeriode
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.builders.forberedDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class DiagnosePerioderRepositoryImplTest {

    @Test
    fun `lagre og hente ut periodiserte diagnoser`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingId = dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        val diagnoser = listOf(
            DiagnoseMedPeriode(
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 6, 30),
                kodeverk = "ICD10",
                diagnosekode = "A01",
                bidiagnoser = listOf("B02", "C03")
            ),
            DiagnoseMedPeriode(
                fom = LocalDate.of(2024, 7, 1),
                tom = LocalDate.of(2024, 12, 31),
                kodeverk = "ICD10",
                diagnosekode = "D04",
                bidiagnoser = emptyList()
            ),
        )

        dataSource.transaction {
            DiagnosePerioderRepositoryImpl(it).lagre(behandlingId, diagnoser)
        }

        val uthentet = dataSource.transaction {
            DiagnosePerioderRepositoryImpl(it).hentForBehandling(behandlingReferanse)
        }

        assertThat(uthentet).containsExactlyInAnyOrderElementsOf(diagnoser)
    }

    @Test
    fun `lagre tom liste gjør ingenting`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingId = dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        dataSource.transaction {
            DiagnosePerioderRepositoryImpl(it).lagre(behandlingId, emptyList())
        }

        val uthentet = dataSource.transaction {
            DiagnosePerioderRepositoryImpl(it).hentForBehandling(behandlingReferanse)
        }

        assertThat(uthentet).isEmpty()
    }
}
