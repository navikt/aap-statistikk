package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryFactory
import no.nav.aap.komponenter.type.Periode
import no.nav.aap.statistikk.behandling.BehandlingId

class ArbeidsopptrappingperioderRepositoryImpl(private val dbConnection: DBConnection) :
    ArbeidsopptrappingperioderRepository {

    companion object : RepositoryFactory<ArbeidsopptrappingperioderRepositoryImpl> {
        override fun konstruer(connection: DBConnection): ArbeidsopptrappingperioderRepositoryImpl {
            return ArbeidsopptrappingperioderRepositoryImpl(connection)
        }
    }

    override fun lagre(
        behandlingId: BehandlingId,
        perioder: List<Periode>
    ) {
        val deleteSql = """
            DELETE FROM arbeidsopptrappingperioder WHERE behandling_id = ?
        """.trimIndent()

        dbConnection.execute(deleteSql) {
            setParams {
                setLong(1, behandlingId.id)
            }
        }

        if (perioder.isEmpty()) {
            return
        }

        val insertSql = """
            INSERT INTO arbeidsopptrappingperioder (behandling_id, fra_dato, til_dato) 
            VALUES (?, ?, ?)
        """.trimIndent()

        dbConnection.executeBatch(insertSql, perioder) {
            setParams {
                setLong(1, behandlingId.id)
                setLocalDate(2, it.fom)
                setLocalDate(3, it.tom)
            }
        }
    }

    override fun hent(behandlingId: BehandlingId): List<Periode>? {
        val behandlingExistsSql = """
            SELECT COUNT(*) as count FROM behandling WHERE id = ?
        """.trimIndent()

        val behandlingExists = dbConnection.queryFirst(behandlingExistsSql) {
            setParams { setLong(1, behandlingId.id) }
            setRowMapper { it.getInt("count") > 0 }
        }

        if (!behandlingExists) {
            return null
        }

        val sql = """
            SELECT fra_dato, til_dato
            FROM arbeidsopptrappingperioder
            WHERE behandling_id = ?
        """.trimIndent()

        return dbConnection.queryList(sql) {
            setParams {
                setLong(1, behandlingId.id)
            }
            setRowMapper {
                Periode(
                    fom = it.getLocalDate("fra_dato"),
                    tom = it.getLocalDate("til_dato")
                )
            }
        }
    }
}