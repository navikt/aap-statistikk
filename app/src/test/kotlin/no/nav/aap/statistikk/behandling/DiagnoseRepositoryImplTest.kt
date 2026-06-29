package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.builders.forberedDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
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
        val oppdatertTid = dataSource.transaction {
            hentOppdatertTid(it, behandlingReferanse)
        }

        assertThat(uthentet).isEqualTo(
            DiagnoseEntity(
                behandlingReferanse = behandlingReferanse,
                kodeverk = "dsdsd",
                diagnosekode = "DD",
                bidiagnoser = listOf("PEST", "KOLERA")
            )
        )
        assertThat(oppdatertTid).isNotNull()

    }

    private fun settInnDiagnose(
        it: DBConnection,
        diagnoseEntity: DiagnoseEntity
    ) = DiagnoseRepositoryImpl(it).lagre(
        diagnoseEntity
    )

    private fun hentOppdatertTid(
        it: DBConnection,
        behandlingReferanse: UUID
    ): LocalDateTime = it.queryFirst(
        """
            SELECT d.oppdatert_tid AS oppdatert_tid
            FROM diagnose d
            JOIN behandling b ON d.behandling_id = b.id
            JOIN behandling_referanse br ON b.referanse_id = br.id
            WHERE br.referanse = ?
        """.trimIndent()
    ) {
        setParams {
            setUUID(1, behandlingReferanse)
        }
        setRowMapper { row ->
            row.getLocalDateTime("oppdatert_tid")
        }
    }
}