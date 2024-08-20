package no.nav.aap.statistikk.hendelser.repository

import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.db.hentGenerertNøkkel
import no.nav.aap.statistikk.db.withinTransaction
import no.nav.aap.statistikk.hendelser.api.MottaStatistikkDTO
import java.sql.Statement
import java.sql.Timestamp
import java.util.*
import javax.sql.DataSource

class HendelsesRepository(private val dataSource: DataSource) : IHendelsesRepository {

    override fun lagreHendelse(hendelse: MottaStatistikkDTO): Int {
        return dataSource.withinTransaction { connection ->
            val personId = hentEllerSettInnPersonId(connection, hendelse.ident)
            val sakId = hentEllerSettInnSak(connection, hendelse.saksnummer, personId)
            val behandlingId = hentEllerSettInnBehandling(connection, hendelse, sakId)

            connection.prepareStatement(
                "INSERT INTO motta_statistikk (behandling_id, sak_id, status) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            ).apply {
                setInt(1, behandlingId)
                setInt(2, sakId)
                setString(3, hendelse.status)
                executeUpdate()
            }.hentGenerertNøkkel()
        }
    }

    override fun hentHendelser(): Collection<MottaStatistikkDTO> {
        return dataSource.withinTransaction { connection ->
            val rs = connection.prepareStatement(
                """SELECT *
                   FROM motta_statistikk
                   LEFT JOIN behandling b ON b.id = motta_statistikk.behandling_id
                   LEFT JOIN sak ms ON motta_statistikk.sak_id = ms.id
                   INNER JOIN person p ON ms.person_id = p.id"""
            ).executeQuery()

            val hendelser = mutableListOf<MottaStatistikkDTO>()
            while (rs.next()) {
                hendelser.add(
                    MottaStatistikkDTO(
                        saksnummer = rs.getString("saksnummer"),
                        behandlingReferanse = UUID.fromString(rs.getString("referanse")),
                        status = rs.getString("status"),
                        behandlingType = TypeBehandling.valueOf(rs.getString("type")),
                        behandlingOpprettetTidspunkt = rs.getTimestamp("opprettet_tid")
                            .toLocalDateTime(),
                        ident = rs.getString("ident"),
                        avklaringsbehov = listOf()
                    )
                )
            }
            hendelser
        }
    }

    private fun hentEllerSettInnPersonId(connection: java.sql.Connection, ident: String): Int {
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
        return connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setString(1, ident)
            stmt.setString(2, ident)
            stmt.executeQuery().use {
                if (it.next()) it.getInt("id") else throw RuntimeException("Person ID ikke funnet.")
            }
        }
    }

    private fun hentEllerSettInnSak(
        connection: java.sql.Connection,
        saksnummer: String,
        personId: Int
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
        return connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setString(1, saksnummer)
            stmt.setInt(2, personId)
            stmt.setString(3, saksnummer)
            stmt.executeQuery().use {
                if (it.next()) it.getInt("id") else throw RuntimeException("Sak ID not found")
            }
        }
    }

    private fun hentEllerSettInnBehandling(
        connection: java.sql.Connection,
        hendelse: MottaStatistikkDTO,
        sakId: Int
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
        return connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setInt(1, sakId)
            stmt.setObject(2, hendelse.behandlingReferanse)
            stmt.setString(3, hendelse.behandlingType.toString())
            stmt.setTimestamp(4, Timestamp.valueOf(hendelse.behandlingOpprettetTidspunkt))
            stmt.setObject(5, hendelse.behandlingReferanse)
            stmt.executeQuery().use {
                if (it.next()) {
                    it.getInt("id")
                } else {
                    throw RuntimeException("Behandling ID for sak $sakId og hendelse ${hendelse.behandlingReferanse} ikke funnet")
                }
            }
        }
    }
}