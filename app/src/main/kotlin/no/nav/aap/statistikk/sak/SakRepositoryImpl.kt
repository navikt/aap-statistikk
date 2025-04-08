package no.nav.aap.statistikk.sak

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.statistikk.person.Person

class SakRepositoryImpl(private val dbConnection: DBConnection) : SakRepository {
    override fun hentSak(sakID: Long): Sak {
        val query = """
SELECT sak.id           as s_id,
       sak.saksnummer   as s_saksnummer,
       sak.person_id    as s_person_id,
       p.ident          as p_ident,
       sh.oppdatert_tid as sh_oppdatert_tid,
       sh.sak_status    as sh_sak_status,
       sh.id            as sh_id
FROM sak
         JOIN person p ON sak.person_id = p.id
         JOIN (SELECT * FROM sak_historikk sh WHERE gjeldende = TRUE) sh ON sh.sak_id = sak.id
WHERE sak.id = ?
        """

        return dbConnection.queryFirst(query) {
            setParams {
                setLong(1, sakID)
            }
            setRowMapper { row ->
                sakRowMapper(row)
            }
        }
    }

    private val HENT_SAK_QUERY = """
SELECT sak.id           as s_id,
       sak.saksnummer   as s_saksnummer,
       sak.person_id    as s_person_id,
       p.ident          as p_ident,
       sh.oppdatert_tid as sh_oppdatert_tid,
       sh.sak_status    as sh_sak_status,
       sh.id            as sh_id
FROM sak
         JOIN person p ON sak.person_id = p.id
         JOIN (SELECT * FROM sak_historikk sh WHERE gjeldende = TRUE) sh ON sh.sak_id = sak.id
WHERE sak.saksnummer = ?
        """

    override fun hentSak(saksnummer: Saksnummer): Sak {
        return dbConnection.queryFirst(HENT_SAK_QUERY) {
            setParams {
                setString(1, saksnummer.value)
            }
            setRowMapper { row ->
                sakRowMapper(row)
            }
        }
    }

    override fun hentSakEllernull(saksnummer: Saksnummer): Sak? {
        return dbConnection.queryFirstOrNull<Sak>(HENT_SAK_QUERY) {
            setParams {
                setString(1, saksnummer.value)
            }
            setRowMapper { row ->
                sakRowMapper(row)
            }
        }
    }

    private fun sakRowMapper(row: Row) = Sak(
        id = row.getLong("s_id"),
        saksnummer = row.getString("s_saksnummer").let(::Saksnummer),
        person = Person(
            ident = row.getString("p_ident"),
            id = row.getLong("s_person_id"),
        ),
        sistOppdatert = row.getLocalDateTime("sh_oppdatert_tid"),
        snapShotId = row.getLong("sh_id"),
        sakStatus = row.getEnum("sh_sak_status")
    )

    override fun settInnSak(sak: Sak): Long {
        val personId = requireNotNull(sak.person.id())

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

        val sakId = dbConnection.queryFirst(query) {
            setParams {
                setString(1, sak.saksnummer.value)
                setLong(2, personId)
                setString(3, sak.saksnummer.value)
            }
            setRowMapper { row ->
                row.getLong("id")
            }
        }

        oppdaterSak(sak.copy(id = sakId))

        return sakId
    }

    override fun oppdaterSak(sak: Sak) {
        val sakId = requireNotNull(sak.id)

        dbConnection.execute("UPDATE sak_historikk SET gjeldende = FALSE where sak_id = ?") {
            setParams { setLong(1, sakId) }
        }

        dbConnection.executeReturnKey("INSERT INTO sak_historikk (gjeldende, oppdatert_tid, sak_id, sak_status) VALUES (?, ?, ?, ?)") {
            setParams {
                var c = 1
                setBoolean(c++, true)
                setLocalDateTime(c++, sak.sistOppdatert)
                setLong(c++, sak.id)
                setString(c++, sak.sakStatus.toString())
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