package no.nav.aap.statistikk.sak

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingId
import java.time.LocalDate

interface IBigQueryKvitteringRepository {
    fun lagreKvitteringForSak(sak: Sak, behandling: Behandling): Long
    fun hentOpplastedeMeldingerFraOgTil(
        fraOgMed: LocalDate,
        tilOgMed: LocalDate
    ): List<BehandlingId>
}

class BigQueryKvitteringRepository(private val dbConnection: DBConnection) :
    IBigQueryKvitteringRepository {
    override fun lagreKvitteringForSak(sak: Sak, behandling: Behandling): Long {
        dbConnection.markerSavepoint()
        val query =
            "INSERT INTO bigquery_kvittering (sak_snapshot_id, behandling_snapshot_id, tidspunkt) VALUES (?, ?, CURRENT_TIMESTAMP)"
        return dbConnection.executeReturnKey(query) {
            setParams {
                setLong(1, sak.snapShotId!!)
                setLong(2, behandling.snapShotId!!)
            }
        }
    }

    override fun hentOpplastedeMeldingerFraOgTil(
        fraOgMed: LocalDate,
        tilOgMed: LocalDate
    ): List<BehandlingId> {
        val query = """
            SELECT b.id as behandling_id
            FROM bigquery_kvittering
                     JOIN behandling_historikk bh on bigquery_kvittering.behandling_snapshot_id = bh.id
                     join behandling b on bh.behandling_id = b.id
            WHERE tidspunkt >= ?
              AND tidspunkt <= ?
        """.trimIndent()

        return dbConnection.queryList(query) {
            setParams {
                setLocalDate(1, fraOgMed)
                setLocalDate(2, tilOgMed)
            }
            setRowMapper { row ->
                BehandlingId(row.getLong("behandling_id"))
            }
        }
    }
}