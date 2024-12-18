package no.nav.aap.statistikk.enhet

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.oppgave.Enhet

class EnhetRepository(private val dbConnection: DBConnection) {
    fun lagreEnhet(enhet: Enhet): Long {
        val sql = """WITH ny_enhet AS (
    INSERT INTO enhet (kode)
    VALUES (?)
    ON CONFLICT DO NOTHING
    RETURNING id
)
SELECT id FROM ny_enhet
UNION ALL
SELECT id FROM enhet WHERE kode = ?;""";

        return dbConnection.queryFirst(sql) {
            setParams {
                setString(1, enhet.kode)
                setString(2, enhet.kode)
            }
            setRowMapper {
                it.getLong("id")
            }
        }
    }

    fun hentEnhet(kode: String): Enhet? {
        val sql = """SELECT id, kode FROM enhet WHERE kode = ?"""

        return dbConnection.queryFirstOrNull(sql) {
            setParams {
                setString(1, kode)
            }
            setRowMapper {
                Enhet(it.getLong("id"), it.getString("kode"))
            }
        }
    }
}