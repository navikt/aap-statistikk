package no.nav.aap.statistikk.testutils.client

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.motor.Motor
import no.nav.aap.motor.testutil.ManuellMotorImpl
import no.nav.aap.oppgave.statistikk.OppgaveHendelse
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.TilbakekrevingsbehandlingOppdatertHendelse
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.statistikk.AppConfig
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.IBigQueryClient
import no.nav.aap.statistikk.bigquery.SchemaRegistry
import no.nav.aap.statistikk.bigquery.schemaRegistryYtelseStatistikk
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.defaultGatewayProvider
import no.nav.aap.statistikk.jobber.LagreAvklaringsbehovHendelseJobb
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.module
import no.nav.aap.statistikk.postgresRepositoryRegistry
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.startUp
import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.DatasetInfo
import no.nav.aap.statistikk.testutils.fakes.FakeBigQueryClient
import org.slf4j.LoggerFactory
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.gcloud.BigQueryEmulatorContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.MountableFile
import java.io.InputStream
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID

private val logger = LoggerFactory.getLogger("TestClientHelpers")

/**
 * @param azureConfig Send inn egen her om det skal gjøres autentiserte kall.
 */
fun <E> testKlient(
    transactionExecutor: TransactionExecutor,
    motor: Motor,
    azureConfig: AzureConfig = AzureConfig(
        clientId = "tilgang",
        jwksUri = "http://localhost:8081/jwks",
        issuer = "tilgang",
        tokenEndpoint = URI.create("http://localhost:8081/jwks"),
        clientSecret = "xxx",
    ),
    lagreStoppetHendelseJobb: LagreStoppetHendelseJobb,
    jobbAppender: JobbAppender,
    test: TestClient.() -> E,
): E? {
    val res: E?

    System.setProperty("azure.openid.config.token.endpoint", azureConfig.tokenEndpoint.toString())
    System.setProperty("azure.app.client.id", azureConfig.clientId)
    System.setProperty("azure.app.client.secret", azureConfig.clientSecret)
    System.setProperty("azure.openid.config.jwks.uri", azureConfig.jwksUri)
    System.setProperty("azure.openid.config.issuer", azureConfig.issuer)
    val randomUUID = UUID.randomUUID()
    System.setProperty("integrasjon.postmottak.azp", randomUUID.toString())
    System.setProperty("integrasjon.oppgave.azp", randomUUID.toString())
    System.setProperty("integrasjon.behandlingsflyt.azp", randomUUID.toString())

    System.setProperty("NAIS_CLUSTER_NAME", "LOCAL")

    System.setProperty("enhet.retry.max.retries", "3")
    System.setProperty("enhet.retry.delay.seconds", "1")

    val restClient = RestClient(
        config = ClientConfig(scope = "AAP_SCOPES"),
        tokenProvider = ClientCredentialsTokenProvider,
        responseHandler = DefaultResponseHandler(),
    )
    motor.start()

    val lagreAvklaringsbehovHendelseJobb = LagreAvklaringsbehovHendelseJobb(jobbAppender)

    val server = embeddedServer(Netty, port = 0) {
        module(
            transactionExecutor,
            jobbAppender,
            azureConfig,
            {},
            lagreStoppetHendelseJobb,
            lagreAvklaringsbehovHendelseJobb,
        )
    }.start()

    val port = runBlocking { server.engine.resolvedConnectors().first().port }

    res = TestClient(restClient, "http://localhost:$port").test()

    motor.stop()
    server.stop(500L, 10_000L)

    return res
}

class TestClient(private val client: RestClient<InputStream>, private val url: String) {
    fun postBehandlingsflytHendelse(
        hendelse: StoppetBehandling
    ) {
        client.post<StoppetBehandling, Any>(
            URI.create("$url/stoppetBehandling"), PostRequest(hendelse)
        )
    }

    fun oppdatertBehandlingHendelse(hendelse: StoppetBehandling) {
        client.post<StoppetBehandling, Any>(
            URI.create("$url/oppdatertBehandling"), PostRequest(hendelse)
        )
    }

    fun postOppgaveData(
        oppgaveHendelse: OppgaveHendelse
    ) {
        client.post<OppgaveHendelse, Any>(
            URI.create("$url/oppgave"), PostRequest(oppgaveHendelse)
        )
    }

    fun postPostmottakHendelse(hendelse: DokumentflytStoppetHendelse) {
        client.post<DokumentflytStoppetHendelse, Any>(
            URI.create("$url/postmottak"), PostRequest(hendelse)
        )
    }

    fun postTilbakekrevingshendelse(hendelse: TilbakekrevingsbehandlingOppdatertHendelse) {
        client.post<TilbakekrevingsbehandlingOppdatertHendelse, Any>(
            URI.create("$url/tilbakekrevingshendelse"), PostRequest(hendelse)
        )
    }
}

fun <E> testKlientNoInjectionManuell(
    dbConfig: DbConfig,
    azureConfig: AzureConfig = AzureConfig(
        clientId = "tilgang",
        jwksUri = "http://localhost:8081/jwks",
        issuer = "tilgang"
    ),
    bigQueryClient: IBigQueryClient = FakeBigQueryClient,
    test: TestClient.(ManuellMotorImpl) -> E,
): E {
    lateinit var motor: ManuellMotorImpl

    System.setProperty("azure.openid.config.token.endpoint", azureConfig.tokenEndpoint.toString())
    System.setProperty("azure.app.client.id", azureConfig.clientId)
    System.setProperty("azure.app.client.secret", azureConfig.clientSecret)
    System.setProperty("azure.openid.config.jwks.uri", azureConfig.jwksUri)
    System.setProperty("azure.openid.config.issuer", azureConfig.issuer)

    System.setProperty("NAIS_CLUSTER_NAME", "LOCAL")

    System.setProperty("enhet.retry.max.retries", "1")
    System.setProperty("enhet.retry.delay.seconds", "0")

    val restClient = RestClient(
        config = ClientConfig(scope = "AAP_SCOPES"),
        tokenProvider = ClientCredentialsTokenProvider,
        responseHandler = DefaultResponseHandler()
    )

    val server = embeddedServer(Netty, port = 0) {
        startUp(
            dbConfig,
            azureConfig,
            bigQueryClient,
            defaultGatewayProvider()
        ) { ds, gp, jobber ->
            ManuellMotorImpl(ds, jobber, postgresRepositoryRegistry, gp).also { motor = it }
        }
    }.start()

    val port = runBlocking { server.engine.resolvedConnectors().first().port }

    val res = TestClient(restClient, "http://localhost:$port").test(motor)

    server.stop(1000L, 10_000L)

    return res
}

fun postgresTestConfig(): DbConfig {
    val postgres = PostgreSQLContainer("postgres:16")
    val dumpFile = Path.of("").toAbsolutePath().resolve("dump.sql")

    val forHostPath = MountableFile.forHostPath(dumpFile)

    postgres.waitingFor(
        HostPortWaitStrategy().withStartupTimeout(
            Duration.of(
                60L,
                ChronoUnit.SECONDS
            )
        )
    ).withCopyFileToContainer(
        forHostPath,
        "/dump.sql"
    )
    postgres.start()

    if (dumpFile.toFile().exists()) {
        val res = postgres.execInContainer("bash", "-c", "psql -U test -d test -f /dump.sql 2>&1")
        if (res.exitCode != 0) {
            println(res)
        }
    } else {
        println("Kan ikke finne databasedump med path $dumpFile")
    }

    val dbConfig = DbConfig(
        jdbcUrl = postgres.jdbcUrl,
        userName = postgres.username,
        password = postgres.password,
        poolSize = AppConfig.hikariMaxPoolSize
    )

    return dbConfig
}

fun bigQueryContainer(): BigQueryConfig {
    val container = BigQueryEmulatorContainer("ghcr.io/goccy/bigquery-emulator:0.6.3")
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

    bigQuery.create(datasetInfo)

    return config
}

val schemaRegistry: SchemaRegistry = schemaRegistryYtelseStatistikk
