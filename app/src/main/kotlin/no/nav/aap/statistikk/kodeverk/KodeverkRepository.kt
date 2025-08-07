package no.nav.aap.statistikk.kodeverk

import no.nav.aap.komponenter.dbconnect.DBConnection

class KodeverkRepository(private val dbConnection: DBConnection) {
    fun hentVilkår(): List<Kodeverk> {
        val sql = """
            select * from kodeverk_vilkar
        """.trimIndent()

        return mapTilKodeverk(sql)
    }

    fun hentResultat(): List<Kodeverk> {
        val sql = """
            select * from kodeverk_resultat
        """

        return mapTilKodeverk(sql)
    }

    fun hentRettighetstype(): List<Kodeverk> {
        val sql = """
            select * from kodeverk_rettighetstype
        """.trimIndent()

        return mapTilKodeverk(sql)
    }

    fun hentBehandlingType(): List<Kodeverk> {
        val sql = """
            select * from kodeverk_behandlingstype
        """.trimIndent()

        return mapTilKodeverk(sql)
    }

    private fun mapTilKodeverk(sql: String): List<Kodeverk> {
        return dbConnection.queryList(sql) {
            setRowMapper {
                Kodeverk(
                    kode = it.getString("kode"),
                    beskrivelse = it.getString("beskrivelse"),
                    gyldigFra = it.getLocalDate("gyldig_fra"),
                    gyldigTil = it.getLocalDateOrNull("gyldig_til")
                )
            }
        }
    }
}