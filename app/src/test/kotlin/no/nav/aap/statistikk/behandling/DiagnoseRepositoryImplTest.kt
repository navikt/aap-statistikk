package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.forberedDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

class DiagnoseRepositoryImplTest {

    @Test
    fun `sette inn diagnose og hente ut igjen`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        dataSource.transaction {
            forberedDatabase(it, behandlingReferanse)
            settInnDiagnose(
                it, DiagnoseEntity(
                    behandlingReferanse = behandlingReferanse,
                    kodeverk = "dsdsd",
                    diagnosekode = "DD",
                    bidiagnoser = listOf("PEST", "KOLERA")
                )
            )
        }

        val uthentet = dataSource.transaction {
            DiagnoseRepositoryImpl(it).hentForBehandling(behandlingReferanse)
        }

        assertThat(uthentet).isEqualTo(
            DiagnoseEntity(
                behandlingReferanse = behandlingReferanse,
                kodeverk = "dsdsd",
                diagnosekode = "DD",
                bidiagnoser = listOf("PEST", "KOLERA")
            )
        )

    }

    private fun settInnDiagnose(
        it: DBConnection,
        diagnoseEntity: DiagnoseEntity
    ) = DiagnoseRepositoryImpl(it).lagre(
        diagnoseEntity
    )
}