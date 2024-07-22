import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.DatasetInfo
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import org.testcontainers.containers.BigQueryEmulatorContainer
import kotlin.math.floor

interface WithBigQueryContainer {

    companion object {
        private val bigQueryContainer = BigQueryEmulatorContainer("ghcr.io/goccy/bigquery-emulator:0.6.3");

        init {
            bigQueryContainer.start()
        }
    }

    fun testBigQueryConfig(): BigQueryConfig {
        val randomPart = floor(Math.random() * 10000).toString().substring(0, 4)
        val datasetName = "dataset-$randomPart"
        val datasetInfo = DatasetInfo.newBuilder(datasetName).build()

        val config = object : BigQueryConfig {
            override val dataset: String
                get() = datasetName

            override fun bigQueryOptions(): BigQueryOptions {
                return BigQueryOptions.newBuilder()
                    .setLocation(bigQueryContainer.emulatorHttpEndpoint)
                    .setProjectId(bigQueryContainer.projectId)
                    .setHost(bigQueryContainer.emulatorHttpEndpoint)
                    .setCredentials(NoCredentials.getInstance())
                    .build()
            }
        }

        config.bigQueryOptions().service.create(datasetInfo)

        return config
    }
}