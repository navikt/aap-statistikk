package no.nav.aap.statistikk.bigquery

import com.google.cloud.bigquery.*
import org.slf4j.LoggerFactory


private val log = LoggerFactory.getLogger(BigQueryClient::class.java)

class BigQueryClient(options: BigQueryConfig) : IBigQueryClient {
    private val bigQuery: BigQuery = options.bigQueryOptions().service
    private val dataset = options.dataset

    private fun sjekkAtDatasetEksisterer(): Boolean {
        log.info("Verifying that $dataset exists.")
        val dataset = bigQuery.getDataset(dataset)

        return dataset != null && dataset.exists()
    }

    override fun <E> create(table: BQTable<E>): Boolean {
        if (!sjekkAtDatasetEksisterer()) {
            throw Exception("Dataset $dataset eksisterer ikke, så kan ikke lage tabell.")
        }

        val name = table.tableName
        val tabell = bigQuery.getTable(TableId.of(dataset, name))

        if (tabell != null && tabell.exists()) {
            log.info("Tabell ${table.tableName} eksisterer allerede.")
            return false
        }

        val schema = table.schema

        val tableDefinition = StandardTableDefinition.newBuilder().setSchema(schema).build()
        val res = bigQuery.create(TableInfo.of(TableId.of(dataset, name), tableDefinition))

        return res.exists()
    }

    override fun <E> insert(table: BQTable<E>, value: E) {
        insertMany(table, listOf(value))
    }

    override fun <E> insertMany(table: BQTable<E>, values: List<E>) {
        val builder = InsertAllRequest.newBuilder(dataset, table.tableName)
        for (value in values) {
            builder.addRow(table.toRow(value))
        }
        val built = builder.build()

        val response = bigQuery.insertAll(built)

        if (response.hasErrors()) {
            log.warn(response.insertErrors.toString())
        }
    }

    override fun <E> read(table: BQTable<E>): List<E> {
        val query = "select * from $dataset.${table.tableName}"

        val config = QueryJobConfiguration.newBuilder(query)
            .setUseLegacySql(false)
            .setDefaultDataset(dataset).build()

        val res = bigQuery.query(config)

        return res.iterateAll().map { row -> table.parseRow(row) }
    }
}