import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.DatasetInfo
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import org.testcontainers.containers.BigQueryEmulatorContainer
import java.util.*
import kotlin.math.floor

interface WithBigQueryContainer {

    companion object {
        private val bigQueryContainer = BigQueryEmulatorContainer("ghcr.io/goccy/bigquery-emulator:0.6.3");

        init {
            bigQueryContainer.start()
        }
    }

    fun testBigQueryConfig(): BigQueryConfig {
        val randomPart = generateRandomString(5)
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

    fun generateRandomString(length: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val sb = StringBuilder()
        val random = Random()
        for (i in 0 until length) {
            val index: Int = random.nextInt(alphabet.length)
            sb.append(alphabet[index])
        }
        return sb.toString()
    }
}