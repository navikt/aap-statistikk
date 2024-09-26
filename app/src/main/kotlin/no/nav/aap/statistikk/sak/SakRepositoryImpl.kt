package no.nav.aap.statistikk.sak

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.statistikk.person.Person

class SakRepositoryImpl(private val dbConnection: DBConnection) : SakRepository {
    override fun hentSak(sakID: Long): Sak {
        val query = """SELECT *
FROM sak
         JOIN person p ON sak.person_id = p.id
WHERE sak.id = ?
        """

        return dbConnection.queryFirst<Sak>(query) {
            setParams {
                setLong(1, sakID)
            }
            setRowMapper { row ->
                sakRowMapper(row)
            }
        }
    }

    private val HENT_SAK_QUERY = """SELECT *
FROM sak
         JOIN person p ON sak.person_id = p.id
WHERE sak.saksnummer = ?
        """

    override fun hentSak(saksnummer: String): Sak {
        return dbConnection.queryFirst(HENT_SAK_QUERY) {
            setParams {
                setString(1, saksnummer)
            }
            setRowMapper { row ->
                sakRowMapper(row)
            }
        }
    }

    override fun hentSakEllernull(saksnummer: String): Sak? {
        return dbConnection.queryFirstOrNull<Sak>(HENT_SAK_QUERY) {
            setParams {
                setString(1, saksnummer)
            }
            setRowMapper { row ->
                sakRowMapper(row)
            }
        }
    }

    private fun sakRowMapper(row: Row) = Sak(
        id = row.getLong("id"),
        saksnummer = row.getString("saksnummer"),
        person = Person(
            ident = row.getString("ident"),
            id = row.getLong("person_id"),
        )
    )

    override fun settInnSak(sak: Sak): Long {
        val personId = requireNotNull(sak.person.id)

        val query = """
            WITH INSERTED AS (
                INSERT INTO sak (saksnummer, person_id) VALUES (?, ?)
                ON CONFLICT (saksnummer) DO NOTHING
                RETURNING id
            )
            SELECT id FROM INSERTED
            UNION ALL
            SELECT id FROM sak WHERE saksnummer = ?
            LIMIT 1
        """

        return dbConnection.queryFirst(query) {
            setParams {
                setString(1, sak.saksnummer)
                setLong(2, personId)
                setString(3, sak.saksnummer)
            }
            setRowMapper { row ->
                row.getLong("id")
            }
        }
    }

    override fun tellSaker(): Int {
        val query = "SELECT COUNT(*) AS total FROM sak"
        return dbConnection.queryFirst<Int>(query) {
            setRowMapper { row ->
                row.getInt("total")
            }
        }
    }
}