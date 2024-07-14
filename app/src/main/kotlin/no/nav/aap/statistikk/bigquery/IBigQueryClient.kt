package no.nav.aap.statistikk.bigquery

interface IBigQueryClient {
    fun <E> create(table: BQTable<E>): Boolean
    fun <E> insert(table: BQTable<E>, value: E)
    fun <E> read(table: BQTable<E>): List<E>
}