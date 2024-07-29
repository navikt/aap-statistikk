package no.nav.aap.statistikk.db

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("WithinTransaction")


class TilkoblingsAvbrudd(message: String?, cause: Throwable?) : Exception(message, cause)

open class TransaksjonsAvbrudd(message: String?, cause: Throwable?) : Exception(message, cause)

class TilbakerullingsAvbrudd(message: String?, cause: Throwable?) : Exception(message, cause)

class EksistererAlleredeAvbrudd(message: String?, cause: Throwable) :
    TransaksjonsAvbrudd(message, cause)

// extension function on datasource
fun <E> DataSource.withinTransaction(block: (Connection) -> E): E {
    // Late init, fordi getConnection teoretisk kan kaste SQLException
    var connection: Connection? = null
    try {
        connection = this.connection
        connection.autoCommit = false

        val res = block(connection)
        connection.commit()
        return res
    } catch (ex: SQLException) {
        // If there were an exception, roll back the transaction
        if (connection != null) {
            try {
                connection.rollback()
                if (ex.sqlState == "23505") {
                    throw EksistererAlleredeAvbrudd(ex.message, ex)
                } else {
                    throw TransaksjonsAvbrudd(ex.message, ex)
                }
            } catch (rbEx: SQLException) {
                log.error("Error during transaction rollback: ${rbEx.message}", rbEx)
                throw TilbakerullingsAvbrudd(rbEx.message, ex)
            }
        }
        log.error("Kunne ikke få tak i Connection-objekt", ex)
        throw TilkoblingsAvbrudd("Kunne ikke få tak i Connection-objekt.", ex)
    } finally {
        // Make sure to close the connection
        if (connection != null) {
            try {
                connection.close()
            } catch (closeEx: SQLException) {
                log.error("Error while closing connection: ${closeEx.message}", closeEx)
            }
        }
    }
}

fun PreparedStatement.hentGenerertNøkkel(): Int {
    val resultSet = this.generatedKeys
    resultSet.next()
    val id = resultSet.getInt(1)
    return id
}