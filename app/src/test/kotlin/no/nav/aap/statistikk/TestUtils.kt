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
import no.nav.aap.statistikk.hendelser.api.MottaStatistikkDTO
import no.nav.aap.statistikk.hendelser.api.TypeBehandling
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.server.authenticate.AzureConfig
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BigQueryEmulatorContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger("TestUtils")

/**
 * @param azureConfig Send inn egen her om det skal gjøres autentiserte kall.
 */
fun <E> testKlient(
    hendelsesRepository: IHendelsesRepository,
    avsluttetBehandlingService: AvsluttetBehandlingService,
    azureConfig: AzureConfig = AzureConfig(
        clientId = "tilgang",
        jwks = URI.create("http://localhost:8081/jwks").toURL(),
        issuer = "tilgang"
    ),
    test: suspend (HttpClient) -> E?
): E? {
    var res: E? = null;

    testApplication {
        application {
            module(hendelsesRepository, avsluttetBehandlingService, azureConfig)
        }
        val client = client.config {
            install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                }
            }
        }

        res = test(client)
    }
    return res
}

fun postgresTestConfig(port: Int? = null): DbConfig {
    val postgres = PostgreSQLContainer("postgres:15")
    if (port != null) {
        postgres.portBindings = listOf("5432:5432")
    }
    postgres.waitingFor(
        HostPortWaitStrategy().withStartupTimeout(
            Duration.of(
                60L,
                ChronoUnit.SECONDS
            )
        )
    )
    postgres.start()

    val dbConfig = DbConfig(
        jdbcUrl = postgres.jdbcUrl,
        userName = postgres.username,
        password = postgres.password
    )
    return dbConfig
}


fun bigQueryContainer(): BigQueryConfig {
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

    logger.info("BigQuery URL: $url")

    val bigQueryOptions: BigQueryOptions = config.bigQueryOptions()
    val bigQuery: BigQuery = bigQueryOptions.service

    // Lag datasett
    bigQuery.create(datasetInfo)

    return config
}

fun opprettTestHendelse(dataSource: DataSource, randomUUID: UUID, saksnummer: String) {
    val hendelse = HendelsesRepository(dataSource)
    hendelse.lagreHendelse(
        MottaStatistikkDTO(
            saksnummer = saksnummer,
            behandlingReferanse = randomUUID,
            behandlingOpprettetTidspunkt = LocalDateTime.now(),
            status = "IVERKSATT",
            behandlingType = TypeBehandling.Førstegangsbehandling,
            ident = "123",
            avklaringsbehov = listOf()
        )
    )
}