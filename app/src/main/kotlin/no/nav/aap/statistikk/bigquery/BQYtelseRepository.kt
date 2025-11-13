package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.behandling.BQYtelseBehandling
import no.nav.aap.statistikk.behandling.BehandlingTabell
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(BQYtelseRepository::class.java)

class BQYtelseRepository(
    private val client: BigQueryClient
) : IBQYtelsesstatistikkRepository {
    private val behandlingTabell = BehandlingTabell()

    private data class TableWithValues<E>(val table: BQTable<E>, val values: List<E>)

    private val valsToCommit = mutableListOf<TableWithValues<*>>()

    private fun <E> addToCommit(
        tabell: BQTable<E>,
        payload: List<E>
    ) {
        val existingEntry = valsToCommit.find { it.table == tabell }
        if (existingEntry != null) {
            @Suppress("UNCHECKED_CAST")
            val typedEntry = existingEntry as TableWithValues<E>
            valsToCommit.remove(existingEntry)
            valsToCommit.add(TableWithValues(tabell, typedEntry.values + payload))
        } else {
            valsToCommit.add(TableWithValues(tabell, payload))
        }
    }

    override fun lagre(payload: BQYtelseBehandling) {
        logger.info("Lagrer BQYtelseBehandling for behandling ${payload.referanse}.")
        client.insert(behandlingTabell, payload)
        addToCommit(behandlingTabell, listOf(payload))
    }

    override fun start() {
        valsToCommit.clear()
    }

    override fun commit() {
        valsToCommit.forEach { tableWithValues ->
            if (tableWithValues.values.isNotEmpty()) {
                logger.info("Lagrer ${tableWithValues.values.size} rader til tabell ${tableWithValues.table.tableName}.")
                insertValues(tableWithValues)
            } else {
                logger.info("Ingen rader å lagre til tabell ${tableWithValues.table.tableName}.")
            }
        }
    }

    private fun <E> insertValues(tableWithValues: TableWithValues<E>) {
        client.insertMany(tableWithValues.table, tableWithValues.values)
    }

    override fun toString(): String {
        return "BQRepository(behandlingTabell=$behandlingTabell, client=$client)"
    }
}
