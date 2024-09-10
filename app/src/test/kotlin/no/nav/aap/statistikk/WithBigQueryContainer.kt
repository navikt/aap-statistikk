package no.nav.aap.statistikk

import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.DatasetInfo
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.testcontainers.containers.BigQueryEmulatorContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.time.Duration
import java.util.*

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.CLASS,
    AnnotationTarget.VALUE_PARAMETER
)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(
    BigQuery.BigQueryExtension::class
)
annotation class BigQuery {
    class BigQueryExtension : ParameterResolver, AfterAllCallback {

        companion object {
            private val bigQueryContainer =
                BigQueryEmulatorContainer("ghcr.io/goccy/bigquery-emulator:0.6.3").waitingFor(
                    HostPortWaitStrategy().withStartupTimeout(
                        Duration.ofSeconds(10)
                    )
                );

            init {
                bigQueryContainer.start()
            }
        }

        private fun testBigQueryConfig(): BigQueryConfig {
            val randomPart = generateRandomString()
            val datasetName = "dataset_$randomPart"
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

        private fun generateRandomString(): String {
            val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val sb = StringBuilder()
            val random = Random()
            for (i in 0 until 5) {
                val index: Int = random.nextInt(alphabet.length)
                sb.append(alphabet[index])
            }
            return sb.toString()
        }

        override fun supportsParameter(
            parameterContext: ParameterContext?,
            extensionContext: ExtensionContext?
        ): Boolean {
            return parameterContext?.isAnnotated(BigQuery::class.java) == true && (parameterContext.parameter.type == BigQueryConfig::class.java)
        }

        override fun resolveParameter(
            parameterContext: ParameterContext?,
            extensionContext: ExtensionContext?
        ): Any {
            return testBigQueryConfig()
        }

        override fun afterAll(context: ExtensionContext?) {
            bigQueryContainer.stop()
        }
    }
}