package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryFactory
import no.nav.aap.statistikk.behandling.BehandlingId

class VedtattStansOpphørRepositoryImpl(private val dbConnection: DBConnection) :
    VedtattStansOpphørRepository {

    companion object : RepositoryFactory<VedtattStansOpphørRepositoryImpl> {
        override fun konstruer(connection: DBConnection) = VedtattStansOpphørRepositoryImpl(connection)
    }

    override fun lagre(behandlingId: BehandlingId, vedtattStansOpphør: List<StansEllerOpphør>) {
        dbConnection.execute(
            """
            DELETE FROM vedtatt_stans_opphor_aarsak
            WHERE vedtatt_stans_opphor_id IN (
                SELECT id FROM vedtatt_stans_opphor WHERE behandling_id = ?
            )
            """.trimIndent()
        ) {
            setParams { setLong(1, behandlingId.id) }
        }

        dbConnection.execute("DELETE FROM vedtatt_stans_opphor WHERE behandling_id = ?") {
            setParams { setLong(1, behandlingId.id) }
        }

        if (vedtattStansOpphør.isEmpty()) {
            return
        }

        val insertStansSql = """
            INSERT INTO vedtatt_stans_opphor (behandling_id, type, fom)
            VALUES (?, ?, ?)
            RETURNING id
        """.trimIndent()

        val insertÅrsakSql = """
            INSERT INTO vedtatt_stans_opphor_aarsak (vedtatt_stans_opphor_id, aarsak)
            VALUES (?, ?)
        """.trimIndent()

        for (stansEllerOpphør in vedtattStansOpphør) {
            val stansId = dbConnection.queryFirst(insertStansSql) {
                setParams {
                    setLong(1, behandlingId.id)
                    setString(2, stansEllerOpphør.type.name)
                    setLocalDate(3, stansEllerOpphør.fom)
                }
                setRowMapper { it.getLong("id") }
            }

            dbConnection.executeBatch(insertÅrsakSql, stansEllerOpphør.årsaker.toList()) {
                setParams {
                    setLong(1, stansId)
                    setString(2, it.name)
                }
            }
        }
    }
}
