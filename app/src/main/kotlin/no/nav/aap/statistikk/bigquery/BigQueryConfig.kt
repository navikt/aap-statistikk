package no.nav.aap.statistikk.bigquery

import com.google.cloud.bigquery.BigQueryOptions

interface BigQueryConfig {
    val dataset: String
    fun bigQueryOptions(): BigQueryOptions
}

class BigQueryConfigFromEnv(override val dataset: String) : BigQueryConfig {
    override fun bigQueryOptions(): BigQueryOptions {
        val projectId = System.getenv("GCP_TEAM_PROJECT_ID")
        BigQueryOptions.DefaultBigQueryFactory()
        return BigQueryOptions
            .newBuilder()
            .setProjectId(projectId)
            .build()
    }
}