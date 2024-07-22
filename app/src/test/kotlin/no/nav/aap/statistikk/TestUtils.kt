package no.nav.aap.statistikk

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.DatasetInfo
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.vilkårsresultat.service.VilkårsResultatService
import org.testcontainers.containers.BigQueryEmulatorContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.time.Duration
import java.time.temporal.ChronoUnit


fun testKlient(
    hendelsesRepository: IHendelsesRepository,
    vilkårsResultatService: VilkårsResultatService,
    avsluttetBehandlingService: AvsluttetBehandlingService,
    test: suspend (HttpClient) -> Unit
) {
    testApplication {
        application {
            module(hendelsesRepository, vilkårsResultatService, avsluttetBehandlingService)
        }
        val client = client.config {
            install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                }
            }
        }

        test(client)
    }
}

data class Triplet(val client: HttpClient, val dbConfig: DbConfig, val bigQueryConfig: BigQueryConfig)

fun testKlientMedTestContainer(
    test: suspend (Triplet) -> Unit
) {
    val pgConfig = postgresTestConfig()
    val bqConfig: BigQueryConfig = bigQueryContainer()
    testApplication {
        application {
            startUp(pgConfig, bqConfig)
        }
        val client = client.config {
            install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                }
            }
        }

        test(Triplet(client, pgConfig, bqConfig))
    }
}

fun postgresTestConfig(): DbConfig {
    val postgres = PostgreSQLContainer<Nothing>("postgres:15")
    postgres.waitingFor(HostPortWaitStrategy().withStartupTimeout(Duration.of(60L, ChronoUnit.SECONDS)))
    postgres.start()

    val dbConfig = DbConfig(jdbcUrl = postgres.jdbcUrl, userName = postgres.username, password = postgres.password)
    return dbConfig
}


private fun bigQueryContainer(): BigQueryConfig {
    val container = BigQueryEmulatorContainer("ghcr.io/goccy/bigquery-emulator:0.6.3");
    container.start()

    val url = container.emulatorHttpEndpoint

    val datasetInfo = DatasetInfo.newBuilder("my-dataset").build()
    val config = object : BigQueryConfig {
        override val dataset: String
            get() = "my-dataset"

        override fun bigQueryOptions(): BigQueryOptions {
            return BigQueryOptions.newBuilder()
                .setLocation(url)
                .setProjectId(container.projectId)
                .setHost(url)
                .setCredentials(NoCredentials.getInstance())
                .build()
        }
    }

    println("URL $url")

    val bigQueryOptions: BigQueryOptions = config.bigQueryOptions()
    val bigQuery: BigQuery = bigQueryOptions.service

    // Lag datasett
    bigQuery.create(datasetInfo)

    return config
}