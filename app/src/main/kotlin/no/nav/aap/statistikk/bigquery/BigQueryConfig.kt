package no.nav.aap.statistikk.bigquery

import com.google.cloud.bigquery.BigQueryOptions

interface BigQueryConfig {
    val dataset: String
    fun bigQueryOptions(): BigQueryOptions
}

class BigQueryConfigFromEnv : BigQueryConfig {
    override val dataset: String
        get() = "tester"

    override fun bigQueryOptions(): BigQueryOptions {
        val projectId = System.getenv("GCP_TEAM_PROJECT_ID")
        BigQueryOptions.DefaultBigQueryFactory()
        return BigQueryOptions
            .newBuilder()
            .setProjectId(projectId)
            .build()
    }
}