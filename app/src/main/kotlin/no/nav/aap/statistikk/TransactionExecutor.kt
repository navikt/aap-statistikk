package no.nav.aap.statistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import javax.sql.DataSource

interface TransactionExecutor {
    fun <E> withinTransaction(block: (connection: DBConnection) -> E): E
}

class FellesKomponentTransactionalExecutor(private val dataSource: DataSource) :
    TransactionExecutor {
    override fun <E> withinTransaction(block: (connection: DBConnection) -> E): E {
        return dataSource.transaction { conn ->
            block(conn)
        }
    }
}

class FellesKomponentConnectionExecutor(private val dbConnection: DBConnection) :
    TransactionExecutor {
    override fun <E> withinTransaction(block: (DBConnection) -> E): E {
        return block(dbConnection)
    }
}