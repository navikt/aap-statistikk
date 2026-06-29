package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryFactory
import no.nav.aap.statistikk.avsluttetbehandling.DiagnoseMedPeriode
import java.util.*

class DiagnosePerioderRepositoryImpl(
    private val dbConnection: DBConnection,
) :
    DiagnosePerioderRepository {

    companion object : RepositoryFactory<DiagnosePerioderRepository> {
        override fun konstruer(connection: DBConnection): DiagnosePerioderRepository {
            return DiagnosePerioderRepositoryImpl(connection)
        }
    }

    override fun lagre(behandlingId: BehandlingId, diagnoser: List<DiagnoseMedPeriode>) {
        if (diagnoser.isEmpty()) return

        val sql = """
            INSERT INTO diagnose_periode (behandling_id, fra_dato, til_dato, kodeverk, diagnosekode, bidiagnoser)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        dbConnection.executeBatch(sql, diagnoser) {
            setParams {
                setLong(1, behandlingId.id)
                setLocalDate(2, it.fom)
                setLocalDate(3, it.tom)
                setString(4, it.kodeverk)
                setString(5, it.diagnosekode)
                setArray(6, it.bidiagnoser)
            }
        }
    }

    override fun hentForBehandling(behandlingReferanse: UUID): List<DiagnoseMedPeriode> {
        val sql = """
            SELECT dp.fra_dato, dp.til_dato, dp.kodeverk, dp.diagnosekode, dp.bidiagnoser
            FROM diagnose_periode dp
            JOIN behandling b ON b.id = dp.behandling_id
            JOIN behandling_referanse br ON br.id = b.referanse_id
            WHERE br.referanse = ?
        """.trimIndent()

        return dbConnection.queryList(sql) {
            setParams { setUUID(1, behandlingReferanse) }
            setRowMapper {
                DiagnoseMedPeriode(
                    fom = it.getLocalDate("fra_dato"),
                    tom = it.getLocalDate("til_dato"),
                    kodeverk = it.getString("kodeverk"),
                    diagnosekode = it.getString("diagnosekode"),
                    bidiagnoser = it.getArray("bidiagnoser", String::class)
                )
            }
        }
    }
}
