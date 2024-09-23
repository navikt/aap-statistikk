package no.nav.aap.statistikk.sak

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.person.Person

class SakRepositoryImpl(private val dbConnection: DBConnection) : SakRepository {
    override fun hentSak(sakID: Long): Sak? {
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
                Sak(
                    id = row.getLong("id"),
                    saksnummer = row.getString("saksnummer"),
                    person = Person(
                        ident = row.getString("ident"),
                        id = row.getLong("person_id"),
                    )
                )
            }
        }
    }

    override fun hentSak(saksnummer: String): Sak? {
        val query = """SELECT *
FROM sak
         JOIN person p ON sak.person_id = p.id
WHERE sak.saksnummer = ?
        """

        return dbConnection.queryFirstOrNull<Sak>(query) {
            setParams {
                setString(1, saksnummer)
            }
            setRowMapper { row ->
                Sak(
                    id = row.getLong("id"),
                    saksnummer = row.getString("saksnummer"),
                    person = Person(
                        ident = row.getString("ident"),
                        id = row.getLong("person_id"),
                    )
                )
            }
        }
    }

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

        return dbConnection.queryFirst<Long>(query) {
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