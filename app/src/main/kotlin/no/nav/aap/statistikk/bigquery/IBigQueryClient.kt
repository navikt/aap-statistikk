package no.nav.aap.statistikk.bigquery

interface IBigQueryClient {
    fun createIfNotExists(name: String): Boolean
    fun insertString(tableName: String, value: String)
    fun read(table: String): MutableList<String>?
}