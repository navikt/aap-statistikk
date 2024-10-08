package no.nav.aap.statistikk.sak

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.behandling.Behandling

interface IBigQueryKvitteringRepository {
    fun lagreKvitteringForSak(sak: Sak, behandling: Behandling): Long
}

class BigQueryKvitteringRepository(private val dbConnection: DBConnection) :
    IBigQueryKvitteringRepository {
    override fun lagreKvitteringForSak(sak: Sak, behandling: Behandling): Long {
        dbConnection.markerSavepoint()
        val query =
            "INSERT INTO bigquery_kvittering (sak_snapshot_id, behandling_snapshot_id, tidspunkt) VALUES (?, ?, CURRENT_TIMESTAMP)"
        return dbConnection.executeReturnKey(query) {
            setParams {
                setLong(1, sak.id!!)
                setLong(2, behandling.id!!)
            }
        }
    }
}