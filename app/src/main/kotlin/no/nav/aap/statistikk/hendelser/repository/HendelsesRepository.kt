package no.nav.aap.statistikk.hendelser.repository

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.api_kontrakt.MottaStatistikkDTO
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import java.util.*

class HendelsesRepository(
    private val dbConnection: DBConnection
) : IHendelsesRepository {

    override fun lagreHendelse(hendelse: MottaStatistikkDTO): Int {
        val personId = hentEllerSettInnPersonId(dbConnection, hendelse.ident)
        val sakId = hentEllerSettInnSak(dbConnection, hendelse.saksnummer, personId)
        val behandlingId = hentEllerSettInnBehandling(dbConnection, hendelse, sakId)

        return dbConnection.executeReturnKey("INSERT INTO motta_statistikk (behandling_id, sak_id, status) VALUES (?, ?, ?)") {
            setParams {
                setInt(1, behandlingId)
                setInt(2, sakId)
                setString(3, hendelse.status)
            }
        }.toInt()
    }

    override fun hentHendelser(): Collection<MottaStatistikkDTO> {
        return dbConnection.queryList<MottaStatistikkDTO>(
            query = """SELECT *
                               FROM motta_statistikk
                               LEFT JOIN behandling b ON b.id = motta_statistikk.behandling_id
                               LEFT JOIN sak ms ON motta_statistikk.sak_id = ms.id
                               INNER JOIN person p ON ms.person_id = p.id""",
        ) {
            setRowMapper { row ->
                MottaStatistikkDTO(
                    saksnummer = row.getString("saksnummer"),
                    behandlingReferanse = UUID.fromString(row.getString("referanse")),
                    status = row.getString("status"),
                    behandlingType = TypeBehandling.valueOf(row.getString("type")),
                    behandlingOpprettetTidspunkt = row.getLocalDateTime("opprettet_tid"),
                    ident = row.getString("ident"),
                    avklaringsbehov = listOf()
                )
            }
        }
    }

    override fun tellHendelser(): Int {
        return dbConnection.queryFirst<Int>("SELECT COUNT(*) FROM motta_statistikk") {
            setRowMapper { it.getInt("count") }
        }
    }

    private fun hentEllerSettInnPersonId(connection: DBConnection, ident: String): Int {
        val query = """
            WITH INSERTED AS (
                INSERT INTO person (ident) VALUES (?)
                ON CONFLICT (ident) DO NOTHING
                RETURNING id
            )
            SELECT id FROM INSERTED
            UNION ALL
            SELECT id FROM person WHERE ident = ?
            LIMIT 1
        """
        return connection.queryFirst<Int>(query) {
            setParams {
                setString(1, ident)
                setString(2, ident)
            }
            setRowMapper { row ->
                row.getInt("id")
            }
        }.toInt()
    }

    private fun hentEllerSettInnSak(
        connection: DBConnection, saksnummer: String, personId: Int
    ): Int {
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
        return connection.queryFirst<Int>(query) {
            setParams {
                setString(1, saksnummer)
                setInt(2, personId)
                setString(3, saksnummer)
            }
            setRowMapper { row ->
                row.getInt("id")
            }
        }
    }

    private fun hentEllerSettInnBehandling(
        connection: DBConnection, hendelse: MottaStatistikkDTO, sakId: Int
    ): Int {
        val query = """
            WITH INSERTED AS (
                INSERT INTO behandling (sak_id, referanse, type, opprettet_tid) VALUES (?, ?, ?, ?)
                ON CONFLICT (referanse) DO NOTHING
                RETURNING id
            )
            SELECT id FROM INSERTED
            UNION ALL
            SELECT id FROM behandling WHERE referanse = ?
            LIMIT 1
        """
        return connection.queryFirst(query) {
            setParams {
                setInt(1, sakId)
                setUUID(2, hendelse.behandlingReferanse)
                setString(3, hendelse.behandlingType.toString())
                setLocalDateTime(4, hendelse.behandlingOpprettetTidspunkt)
                setUUID(5, hendelse.behandlingReferanse)
            }
            setRowMapper { row ->
                row.getInt("id")
            }
        }
    }
}