package no.nav.aap.statistikk.tilbakekreving

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.komponenter.repository.RepositoryFactory
import no.nav.aap.statistikk.sak.Saksnummer
import java.util.UUID

interface TilbakekrevingHendelseRepository : Repository {
    fun lagre(sakId: Long, hendelse: TilbakekrevingHendelse)
    fun hent(behandlingRef: String): TilbakekrevingHendelse?
}

class TilbakekrevingHendelseRepositoryImpl(private val dbConnection: DBConnection) :
    TilbakekrevingHendelseRepository {

    companion object : RepositoryFactory<TilbakekrevingHendelseRepository> {
        override fun konstruer(connection: DBConnection): TilbakekrevingHendelseRepository {
            return TilbakekrevingHendelseRepositoryImpl(connection)
        }
    }

    override fun lagre(sakId: Long, hendelse: TilbakekrevingHendelse) {
        val sql = """
            INSERT INTO behandling_tilbakebetaling(
                sak_id,
                behandling_ref,
                behandling_status,
                sak_opprettet,
                totalt_feilutbetalt_belop,
                saksbehandling_url,
                opprettet_tid
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        dbConnection.execute(sql) {
            setParams {
                setLong(1, sakId)
                setString(2, hendelse.behandlingRef)
                setEnumName(3, hendelse.behandlingStatus)
                setLocalDateTime(4, hendelse.sakOpprettet)
                setBigDecimal(5, hendelse.totaltFeilutbetaltBeløp)
                setString(6, hendelse.saksbehandlingURL)
                setLocalDateTime(7, hendelse.opprettetTid)
            }
        }
    }

    override fun hent(behandlingRef: String): TilbakekrevingHendelse? {
        val sql = """
            SELECT s.saksnummer, th.behandling_ref, th.behandling_status, th.sak_opprettet,
                   th.totalt_feilutbetalt_belop, th.saksbehandling_url, th.opprettet_tid
            FROM behandling_tilbakebetaling th
            JOIN sak s ON th.sak_id = s.id
            WHERE th.behandling_ref = ?
            ORDER BY th.id DESC
            LIMIT 1
        """.trimIndent()

        return dbConnection.queryFirstOrNull(sql) {
            setParams { setString(1, behandlingRef) }
            setRowMapper { row ->
                TilbakekrevingHendelse(
                    saksnummer = Saksnummer(row.getString("saksnummer")),
                    behandlingRef = row.getString("behandling_ref"),
                    behandlingStatus = TilbakekrevingBehandlingStatus.valueOf(row.getString("behandling_status")),
                    sakOpprettet = row.getLocalDateTime("sak_opprettet"),
                    totaltFeilutbetaltBeløp = row.getBigDecimal("totalt_feilutbetalt_belop"),
                    saksbehandlingURL = row.getString("saksbehandling_url"),
                    opprettetTid = row.getLocalDateTime("opprettet_tid"),
                )
            }
        }
    }
}
