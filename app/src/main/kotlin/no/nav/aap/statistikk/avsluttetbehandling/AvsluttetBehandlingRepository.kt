package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.statistikk.api_kontrakt.AvsluttetBehandlingDTO

class AvsluttetBehandlingRepository(private val connection: DBConnection) {
    fun lagre(behandling: AvsluttetBehandlingDTO): Long {
        val jsonString = DefaultJsonMapper.toJson(behandling)

        val id =
            connection.executeReturnKey("INSERT INTO avsluttet_behandling(payload) VALUES(?) RETURNING id") {
                setParams { setString(1, jsonString) }
            }

        return id
    }

    fun hent(id: Long): AvsluttetBehandlingDTO {
        val sql = "SELECT payload FROM avsluttet_behandling WHERE id = ?"
        return connection.queryFirst(sql) {
            setParams { setLong(1, id) }

            setRowMapper { row ->
                DefaultJsonMapper.fromJson<AvsluttetBehandlingDTO>(row.getString("payload"))
            }
        }
    }
}