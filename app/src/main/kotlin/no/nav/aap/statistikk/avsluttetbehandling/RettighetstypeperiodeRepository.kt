package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryFactory
import org.slf4j.LoggerFactory
import java.util.*

class RettighetstypeperiodeRepository(private val dbConnection: DBConnection) :
    IRettighetstypeperiodeRepository {

    companion object : RepositoryFactory<IRettighetstypeperiodeRepository> {
        override fun konstruer(connection: DBConnection): IRettighetstypeperiodeRepository {
            return RettighetstypeperiodeRepository(connection)
        }
    }

    private val logger = LoggerFactory.getLogger(RettighetstypeperiodeRepository::class.java)

    override fun lagre(
        behandlingReferanse: UUID,
        rettighetstypePeriode: List<RettighetstypePeriode>
    ) {
        val deletePerioderSql = """
            DELETE FROM rettighetstypeperioder WHERE rettighetstype_id IN (
                SELECT r.id FROM rettighetstype r
                JOIN behandling b ON r.behandling_id = b.id
                JOIN behandling_referanse br ON b.referanse_id = br.id
                WHERE br.referanse = ?
            )
        """.trimIndent()
        val deleteRettighetstypeSql = """
            DELETE FROM rettighetstype WHERE behandling_id = (
                SELECT b.id FROM behandling b
                JOIN behandling_referanse br ON b.referanse_id = br.id
                WHERE br.referanse = ?
            )
        """.trimIndent()

        dbConnection.execute(deletePerioderSql) { setParams { setUUID(1, behandlingReferanse) } }
        dbConnection.execute(deleteRettighetstypeSql) { setParams { setUUID(1, behandlingReferanse) } }

        val sql = """
            INSERT INTO rettighetstype (behandling_id)
VALUES ((SELECT b.id
         FROM behandling b
                  join behandling_referanse br on b.referanse_id = br.id
         WHERE br.referanse = ?));
        """.trimIndent()
        val key = dbConnection.executeReturnKey(sql) {
            setParams {
                setUUID(1, behandlingReferanse)
            }
        }

        val sql2 = """
            insert into rettighetstypeperioder (rettighetstype_id, fra_dato, til_dato, rettighetstype) values (?, ?, ?, ?);
        """.trimIndent()

        dbConnection.executeBatch(sql2, rettighetstypePeriode) {
            setParams {
                setLong(1, key)
                setLocalDate(2, it.fraDato)
                setLocalDate(3, it.tilDato)
                setString(4, it.rettighetstype.name)
            }

        }
    }

    override fun hent(behandlingReferanse: UUID): List<RettighetstypePeriode> {
        val sql = """
            SELECT rp.fra_dato, rp.til_dato, rp.rettighetstype
            FROM rettighetstypeperioder rp
                     JOIN rettighetstype r ON rp.rettighetstype_id = r.id
                     JOIN behandling b ON r.behandling_id = b.id
                     JOIN behandling_referanse br ON b.referanse_id = br.id
            WHERE br.referanse = ?;
        """.trimIndent()

        return dbConnection.queryList(sql) {
            setParams {
                setUUID(1, behandlingReferanse)
            }
            setRowMapper {
                RettighetstypePeriode(
                    fraDato = it.getLocalDate("fra_dato"),
                    tilDato = it.getLocalDate("til_dato"),
                    rettighetstype = it.getEnum("rettighetstype")
                )
            }
        }
    }
}