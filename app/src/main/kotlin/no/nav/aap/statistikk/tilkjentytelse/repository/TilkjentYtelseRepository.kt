package no.nav.aap.statistikk.tilkjentytelse.repository

import no.nav.aap.statistikk.db.hentGenerertNøkkel
import no.nav.aap.statistikk.db.withinTransaction
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import org.slf4j.LoggerFactory
import java.sql.Statement
import java.sql.Types
import java.util.*
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger(TilkjentYtelseRepository::class.java)

class TilkjentYtelseRepository(private val dataSource: DataSource) {
    fun lagreTilkjentYtelse(tilkjentYtelse: TilkjentYtelseEntity): Int {
        return dataSource.withinTransaction { connection ->
            val sql = "INSERT INTO TILKJENT_YTELSE (saksnummer, behandlingsreferanse) VALUES (?, ?)"

            val preparedStatement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).apply {
                    setString(1, tilkjentYtelse.saksnummer)
                    setObject(2, tilkjentYtelse.behandlingsReferanse, Types.OTHER)
                    executeUpdate()
                }

            val nøkkel = preparedStatement.hentGenerertNøkkel()

            tilkjentYtelse.perioder.forEach { periode ->
                logger.info("Setter inn tilkjentytelse-periode $periode med ID: $nøkkel")
                val periodeSql =
                    "INSERT INTO TILKJENT_YTELSE_PERIODE (FRA_DATO, TIL_DATO, DAGSATS, GRADERING, TILKJENT_YTELSE_ID) VALUES (?, ?, ?, ?, ?)"

                val periodeStatement = connection.prepareStatement(periodeSql)
                periodeStatement.setObject(1, java.sql.Date.valueOf(periode.fraDato))
                periodeStatement.setObject(2, java.sql.Date.valueOf(periode.tilDato))
                periodeStatement.setObject(3, periode.dagsats)
                periodeStatement.setObject(4, periode.gradering)
                periodeStatement.setInt(5, nøkkel)
                periodeStatement.executeUpdate()
            }

            nøkkel
        }
    }

    fun hentTilkjentYtelse(tilkjentYtelseId: Int): TilkjentYtelse? {
        return dataSource.withinTransaction { connection ->
            val preparedStatement = connection.prepareStatement(
                """SELECT *
FROM tilkjent_ytelse_periode
         left join tilkjent_ytelse on tilkjent_ytelse.id = tilkjent_ytelse_periode.tilkjent_ytelse_id
WHERE tilkjent_ytelse.id = ?"""
            ).apply {
                setInt(1, tilkjentYtelseId)
                executeQuery()
            }

            val resultSet = preparedStatement.resultSet
            if (resultSet.next()) {
                val id = resultSet.getLong("id")
                logger.info("ID: $id")
                val saksnummer = "xxxx" // resultSet.getString("saksnummer")
                val behandlingsReferanse = UUID.randomUUID() // !!!

                val perioder = mutableListOf<TilkjentYtelsePeriode>()
                do  {
                    val fraDato = resultSet.getDate("fra_dato").toLocalDate()
                    val tilDato = resultSet.getDate("til_dato").toLocalDate()
                    val dagsats = resultSet.getDouble("dagsats")
                    val gradering = resultSet.getDouble("gradering")
                    perioder.add(TilkjentYtelsePeriode(fraDato, tilDato, dagsats, gradering))
                } while (resultSet.next())

                TilkjentYtelse(saksnummer, behandlingsReferanse, perioder)
            } else {
                null
            }
        }
    }
}