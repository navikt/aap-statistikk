package no.nav.aap.statistikk.hendelser.repository

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.behandling.BehandlingId
import java.util.*

class HendelsesRepository(
    private val dbConnection: DBConnection,
) : IHendelsesRepository {

    override fun lagreHendelse(hendelse: StoppetBehandling, sakId: Long, behandlingId: BehandlingId): Int {
        val versjonId = dbConnection.executeReturnKey("INSERT INTO versjon (versjon) VALUES (?)") {
            setParams {
                setString(1, hendelse.versjon)
            }
        }

        return dbConnection.executeReturnKey("INSERT INTO motta_statistikk (behandling_id, sak_id, status, versjon_id) VALUES (?, ?, ?, ?)") {
            setParams {
                setLong(1, behandlingId)
                setLong(2, sakId)
                setString(3, hendelse.status)
                setLong(4, versjonId)
            }
        }.toInt()
    }

    override fun hentHendelser(): Collection<StoppetBehandling> {
        return dbConnection.queryList(
            query = """SELECT *
                               FROM motta_statistikk
                               LEFT JOIN versjon v ON motta_statistikk.versjon_id = v.id
                               LEFT JOIN behandling b ON b.id = motta_statistikk.behandling_id
                               LEFT JOIN sak ms ON motta_statistikk.sak_id = ms.id
                               INNER JOIN person p ON ms.person_id = p.id""",
        ) {
            setRowMapper { row ->
                StoppetBehandling(
                    saksnummer = row.getString("saksnummer"),
                    behandlingReferanse = UUID.fromString(row.getString("referanse")),
                    status = row.getString("status"),
                    behandlingType = TypeBehandling.valueOf(row.getString("type")),
                    behandlingOpprettetTidspunkt = row.getLocalDateTime("opprettet_tid"),
                    ident = row.getString("ident"),
                    avklaringsbehov = listOf(),
                    versjon = row.getString("versjon")
                )
            }
        }
    }

    override fun tellHendelser(): Int {
        return dbConnection.queryFirst("SELECT COUNT(*) FROM motta_statistikk") {
            setRowMapper { it.getInt("count") }
        }
    }
}