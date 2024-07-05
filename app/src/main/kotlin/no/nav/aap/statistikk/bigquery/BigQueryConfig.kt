package no.nav.aap.statistikk.bigquery

import com.google.cloud.NoCredentials.getInstance
import com.google.cloud.bigquery.BigQueryOptions

data class BigQueryConfig(val dataset: String, val url: String, val projectId: String) {
    companion object {
        fun fromEnvironment(): BigQueryConfig {
            val url = System.getenv("url")
            val dataset = System.getenv("dataset")
            val projectId = System.getenv("projectId")

            return BigQueryConfig(dataset = dataset, url = url, projectId = projectId)
        }
    }

    fun bigQueryOptions(): BigQueryOptions {
        return BigQueryOptions
            .newBuilder()
            .setProjectId(projectId)
            .setHost(url)
            .setLocation(url)
            .setCredentials(getInstance())
            .build()
    }
}