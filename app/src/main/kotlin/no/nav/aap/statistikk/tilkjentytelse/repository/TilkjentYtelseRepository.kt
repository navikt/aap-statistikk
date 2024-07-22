package no.nav.aap.statistikk.tilkjentytelse.repository

import no.nav.aap.statistikk.db.hentGenerertNøkkel
import no.nav.aap.statistikk.db.withinTransaction
import java.sql.Statement
import javax.sql.DataSource

class TilkjentYtelseRepository(private val dataSource: DataSource) {
    fun lagreTilkjentYtelse(tilkjentYtelsePeriodeEntity: TilkjentYtelseEntity): Int {
        return dataSource.withinTransaction { connection ->
            val sql = "INSERT INTO TILKJENT_YTELSE DEFAULT VALUES"

            val preparedStatement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).apply {
                    executeUpdate()
                }

            val nøkkel = preparedStatement.hentGenerertNøkkel()

            tilkjentYtelsePeriodeEntity.perioder.forEach { periode ->
                val periodeSql =
                    "INSERT INTO TILKJENT_YTELSE_PERIODE (FRA_DATO, TIL_DATO, DAGSATS, GRADERING, TILKJENT_YTELSE_ID) VALUES (?, ?, ?, ?, ?)"

                val periodeStatement = connection.prepareStatement(periodeSql)
                periodeStatement.setObject(1, periode.fraDato)
                periodeStatement.setObject(2, periode.tilDato)
                periodeStatement.setObject(3, periode.dagsats)
                periodeStatement.setObject(4, periode.gradering)
                periodeStatement.setInt(5, nøkkel)
                periodeStatement.executeUpdate()
            }

            nøkkel
        }
    }
}