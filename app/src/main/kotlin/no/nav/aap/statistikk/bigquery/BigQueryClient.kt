package no.nav.aap.statistikk.bigquery

import com.google.cloud.bigquery.*
import org.slf4j.LoggerFactory


private val log = LoggerFactory.getLogger(BigQueryClient::class.java)

class BigQueryClient(options: BigQueryConfig, private val schemaRegistry: SchemaRegistry) :
    IBigQueryClient {
    private val bigQuery: BigQuery = options.bigQueryOptions().service
    private val dataset = options.dataset

    private fun sjekkAtDatasetEksisterer(): Boolean {
        log.info("Verifying that $dataset exists.")
        val dataset = bigQuery.getDataset(dataset)

        return dataset != null && dataset.exists()
    }

    init {
        migrate()
    }

    fun migrate() {
        schemaRegistry.values.forEach {
            if (!exists(it)) {
                create(it)
            } else {
                migrateFields(it)
            }
        }
    }

    /**
     * Oppdaterer skjema med nyeste versjon av skjemaet.
     *
     * NB! Kun noen få operasjoner er mulig. Spesifikt å legge til felter, og å gjøre kolonner nullable.
     */
    fun migrateFields(table: BQTable<*>) {
        log.info("Oppdaterer skjema for tabell ${table.tableName}")
        val existingTable = bigQuery.getTable(TableId.of(dataset, table.tableName))

        val updatedTable = existingTable.toBuilder().setDefinition(StandardTableDefinition.of(table.schema)).build()

        updatedTable.update()
        // Samme måte som her: https://github.com/navikt/yrkesskade/blob/b6da4d18d023007f1cd1fd7821e3704aa3a0d051/libs/bigquery/src/main/kotlin/no/nav/yrkesskade/bigquery/client/DefaultBigQueryClient.kt#L96

        log.info("Oppdatert tabell ${table.tableName}")
    }

    private fun exists(table: BQTable<*>): Boolean {
        val table = bigQuery.getTable(TableId.of(dataset, table.tableName))
        return table != null && table.exists()
    }

    override fun <E> create(table: BQTable<E>): Boolean {
        if (!sjekkAtDatasetEksisterer()) {
            throw Exception("Dataset $dataset eksisterer ikke, så kan ikke lage tabell.")
        }

        val name = table.tableName

        if (exists(table)) {
            log.info("Tabell $name eksisterer allerede.")
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
            log.warn("Error ved insert. Tabell: ${table.tableName}. Feilmelding: ${response.insertErrors}.")
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