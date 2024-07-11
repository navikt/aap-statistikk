package no.nav.aap.statistikk.db

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("WithinTransaction")


class TilkoblingsAvbrudd(message: String?) : Exception(message)

class TransaksjonsAvbrudd(message: String?) : Exception(message)

class TilbakerullingsAvbrudd(message: String?) : Exception(message)

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
        // If there was an exception, roll back the transaction
        if (connection != null) {
            try {
                connection.rollback()
                throw TransaksjonsAvbrudd(ex.message)
            } catch (rbEx: SQLException) {
                log.error("Error during transaction rollback: ${rbEx.message}", rbEx)
                throw TilbakerullingsAvbrudd(rbEx.message)
            }
        }
        log.error("Kunne ikke få tak i Connection-objekt", ex)
        throw TilkoblingsAvbrudd("Kunne ikke få tak i Connection-objekt.")
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