package no.nav.aap.statistikk.bigquery

import com.google.cloud.bigquery.*
import org.slf4j.LoggerFactory


val log = LoggerFactory.getLogger(BigQueryClient::class.java)

class BigQueryClient(options: BigQueryConfig) : IBigQueryClient {
    private val bigQuery: BigQuery = options.bigQueryOptions().service
    private val dataset = options.dataset

    private fun sjekkAtDatasetEksisterer(): Boolean {
        log.info("Verifying that $dataset exists.")
        val dataset1 = bigQuery.getDataset(dataset)

        return dataset1 != null && dataset1.exists()
    }

    override fun createIfNotExists(name: String): Boolean {
        if (!sjekkAtDatasetEksisterer()) {
            throw Exception("Dataset $dataset eksisterer ikke, så kan ikke lage tabell.")
        }

        val tabell = bigQuery.getTable(TableId.of(dataset, name))
        if (tabell != null) return false

        val field = Field.of("column_name", StandardSQLTypeName.STRING)
        val schema = Schema.of(field)

        val tableDefinition = StandardTableDefinition.newBuilder().setSchema(schema).build()
        val res = bigQuery.create(TableInfo.of(TableId.of(dataset, name), tableDefinition))

        return res.exists()
    }

    override fun insertString(tableName: String, value: String) {
        val addRow = InsertAllRequest.newBuilder(dataset, tableName)
            .addRow(InsertAllRequest.RowToInsert.of(value.hashCode().toString(), mapOf("column_name" to value)))
            .build()

        bigQuery.insertAll(addRow)
    }


    override fun read(table: String): MutableList<String>? {
        val query = "select * from $dataset.$table"
        val config = QueryJobConfiguration.newBuilder(query).build()

        val res = bigQuery.query(config)

        val asd = res.streamAll().map { row -> row.get("column_name").value as String }.toList()

        return asd
    }
}