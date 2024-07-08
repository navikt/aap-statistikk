package no.nav.aap.statistikk.api

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.DatasetInfo
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import no.nav.aap.statistikk.DbConfig
import no.nav.aap.statistikk.Flyway
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.module
import org.testcontainers.containers.BigQueryEmulatorContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.sql.DataSource


fun testKlient(IHendelsesRepository: IHendelsesRepository, test: suspend (HttpClient) -> Unit) {
    testApplication {
        application {
            module(IHendelsesRepository)
        }
        val client = client.config {
            install(ContentNegotiation) {
                jackson { }
            }
        }

        test(client)
    }
}

fun postgresDataSource(): DataSource {
    val postgres = PostgreSQLContainer<Nothing>("postgres:15")
    postgres.waitingFor(HostPortWaitStrategy().withStartupTimeout(Duration.of(60L, ChronoUnit.SECONDS)))
    postgres.start()

    val dbConfig = DbConfig(jdbcUrl = postgres.jdbcUrl, userName = postgres.username, password = postgres.password)

    val dataSource = Flyway().createAndMigrateDataSource(dbConfig)
    return dataSource
}

fun bigQueryContainer(): BigQueryConfig {
    val container = BigQueryEmulatorContainer("ghcr.io/goccy/bigquery-emulator:0.4.3");
    container.start()

    val url = container.emulatorHttpEndpoint

    val datasetInfo = DatasetInfo.newBuilder("my-dataset").build()
    val config = BigQueryConfig(url = url, projectId = container.projectId, dataset = "my-dataset")

    val bigQueryOptions: BigQueryOptions = config.bigQueryOptions()
    val bigQuery: BigQuery = bigQueryOptions.service

    // Lag datasett
    bigQuery.create(datasetInfo)

    return config
}