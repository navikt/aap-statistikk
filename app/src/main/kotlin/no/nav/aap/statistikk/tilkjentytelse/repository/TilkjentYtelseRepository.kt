package no.nav.aap.statistikk.tilkjentytelse.repository

import no.nav.aap.statistikk.db.hentGenerertNøkkel
import no.nav.aap.statistikk.db.withinTransaction
import java.sql.Statement
import javax.sql.DataSource

class TilkjentYtelseRepository(private val dataSource: DataSource) {

    fun lagreTilkjentYtelse(tilkjentYtelsePeriodeEntity: TilkjentYtelseEntity) {
        return dataSource.withinTransaction { connection ->
            val sql = "INSERT INTO TILKJENT_YTELSE DEFAULT VALUES"
            val statement = connection.prepareStatement(sql)

            val preparedStatement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).apply {
                    executeUpdate()
                }

            val nøkkel = preparedStatement.hentGenerertNøkkel()

        }
    }
}