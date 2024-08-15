package no.nav.aap.statistikk.hendelser.repository

import no.nav.aap.statistikk.db.hentGenerertNøkkel
import no.nav.aap.statistikk.db.withinTransaction
import no.nav.aap.statistikk.hendelser.api.MottaStatistikkDTO
import no.nav.aap.statistikk.hendelser.api.TypeBehandling
import java.sql.Statement
import java.sql.Timestamp
import java.util.*
import javax.sql.DataSource

class HendelsesRepository(private val dataSource: DataSource) : IHendelsesRepository {
    override fun lagreHendelse(hendelse: MottaStatistikkDTO): Int {
        // TODO: bedre transaction-håndtering
        // Ie: mer global. Men vil helst unngå å passe connection overalt...
        var personIdFraQuery: Int? = null;
        return dataSource.withinTransaction { connection ->
            val settInnPersonRespons = connection.prepareStatement(
                """WITH INSERTED AS (
    INSERT INTO person (ident)
        VALUES (?)
        ON CONFLICT (ident) DO NOTHING
        RETURNING id)
SELECT id
FROM INSERTED
UNION ALL
SELECT id FROM person WHERE ident = ?
LIMIT 1;
""",
                Statement.RETURN_GENERATED_KEYS
            ).apply {
                setString(1, hendelse.ident)
                setString(2, hendelse.ident)
                executeQuery().use {
                    while (resultSet.next()) {
                        personIdFraQuery = resultSet.getInt("id");
                    }
                }
            }

            val personId = requireNotNull(personIdFraQuery)

            var sakIdFraQuery: Int? = null;
            connection.prepareStatement(
                """WITH INSERTED
     AS (INSERT INTO sak (saksnummer, person_id) VALUES (?, ?) ON CONFLICT (saksnummer) DO NOTHING RETURNING id)
select id
from INSERTED
UNION ALL
SELECT id FROM sak WHERE saksnummer = ?
limit 1;""",
                Statement.RETURN_GENERATED_KEYS
            )
                .apply {
                    setString(1, hendelse.saksnummer)
                    setString(3, hendelse.saksnummer)
                    setInt(2, personId)
                    executeQuery().use {
                        while (resultSet.next()) {
                            sakIdFraQuery = resultSet.getInt("id");
                        }
                    }
                }

            val sakNøkkel = requireNotNull(sakIdFraQuery)

            var behandlingIdFraQuery: Int? = null;
            val settInnBehandling =
                connection.prepareStatement(
                    """WITH INSERTED
         AS (INSERT INTO behandling (sak_id, referanse, type, opprettet_tid) VALUES (?, ?, ?, ?) ON CONFLICT (referanse) DO NOTHING RETURNING id)
select id
from INSERTED
UNION ALL
SELECT id FROM behandling WHERE referanse = ?
limit 1;""",
                    Statement.RETURN_GENERATED_KEYS
                )
                    .apply {
                        setInt(1, sakNøkkel)
                        setObject(2, hendelse.behandlingReferanse)
                        setObject(5, hendelse.behandlingReferanse)
                        setString(3, hendelse.behandlingType.toString())
                        setTimestamp(
                            4,
                            Timestamp.valueOf(hendelse.behandlingOpprettetTidspunkt)
                        )
                        executeQuery().use {
                            while (resultSet.next()) {
                                behandlingIdFraQuery = resultSet.getInt("id");
                            }
                        }
                    }

            val behandlingId = requireNotNull(behandlingIdFraQuery)

            val returnedValue = connection.prepareStatement(
                "INSERT INTO motta_statistikk (behandling_id, sak_id, status) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            ).apply {
                setInt(1, behandlingId)
                setInt(2, sakNøkkel)
                setString(3, hendelse.status)
                executeUpdate()
            }
            returnedValue.hentGenerertNøkkel()
        }
    }

    override fun hentHendelser(): Collection<MottaStatistikkDTO> {
        return dataSource.withinTransaction { connection ->
            val rs = connection.prepareStatement(
                """select *
from motta_statistikk
         left join behandling b on b.id = motta_statistikk.behandling_id
         left join sak ms on motta_statistikk.sak_id = ms.id
         inner join person p on ms.person_id = p.id;""", Statement.RETURN_GENERATED_KEYS
            ).executeQuery()

            val hendelser = mutableListOf<MottaStatistikkDTO>()

            while (rs.next()) {
                val saksNummer = rs.getString("saksnummer")
                val status = rs.getString("status")
                val behandlingType = rs.getString("type")
                val referanse = UUID.fromString(rs.getString("referanse"))
                val opprettetTidspunkt = rs.getTimestamp("opprettet_tid").toLocalDateTime()
                val ident = rs.getString("ident")

                // TODO
                hendelser.add(
                    MottaStatistikkDTO(
                        saksnummer = saksNummer,
                        behandlingReferanse = referanse,
                        status = status,
                        behandlingType = TypeBehandling.valueOf(behandlingType),
                        behandlingOpprettetTidspunkt = opprettetTidspunkt,
                        ident = ident,
                        avklaringsbehov = listOf()
                    )
                )
            }

            hendelser
        }
    }
}