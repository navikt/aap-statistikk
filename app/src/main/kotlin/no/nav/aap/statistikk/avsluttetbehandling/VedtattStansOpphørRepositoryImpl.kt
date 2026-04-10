package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryFactory
import no.nav.aap.statistikk.behandling.BehandlingId
import java.time.LocalDate

class VedtattStansOpphørRepositoryImpl(private val dbConnection: DBConnection) :
    VedtattStansOpphørRepository {

    companion object : RepositoryFactory<VedtattStansOpphørRepositoryImpl> {
        override fun konstruer(connection: DBConnection): VedtattStansOpphørRepositoryImpl {
            return VedtattStansOpphørRepositoryImpl(connection)
        }
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

    override fun hent(behandlingId: BehandlingId): List<StansEllerOpphør>? {
        val behandlingFinnesSql = "SELECT COUNT(*) as count FROM behandling WHERE id = ?"
        val behandlingFinnes = dbConnection.queryFirst(behandlingFinnesSql) {
            setParams { setLong(1, behandlingId.id) }
            setRowMapper { it.getInt("count") > 0 }
        }

        if (!behandlingFinnes) {
            return null
        }

        val sql = """
            SELECT s.id, s.type, s.fom, a.aarsak
            FROM vedtatt_stans_opphor s
            LEFT JOIN vedtatt_stans_opphor_aarsak a ON a.vedtatt_stans_opphor_id = s.id
            WHERE s.behandling_id = ?
            ORDER BY s.id
        """.trimIndent()

        data class Rad(val id: Long, val type: String, val fom: LocalDate, val aarsak: String?)

        val rader = dbConnection.queryList(sql) {
            setParams { setLong(1, behandlingId.id) }
            setRowMapper {
                Rad(
                    id = it.getLong("id"),
                    type = it.getString("type"),
                    fom = it.getLocalDate("fom"),
                    aarsak = it.getString("aarsak")
                )
            }
        }

        return rader
            .groupBy { Triple(it.id, it.type, it.fom) }
            .map { (key, raderForStans) ->
                StansEllerOpphør(
                    type = StansType.valueOf(key.second),
                    fom = key.third,
                    årsaker = raderForStans.mapNotNull { it.aarsak }
                        .map { Avslagsårsak.valueOf(it) }
                        .toSet()
                )
            }
    }
}
