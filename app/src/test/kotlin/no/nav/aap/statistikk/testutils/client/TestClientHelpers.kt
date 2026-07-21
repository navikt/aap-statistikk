package no.nav.aap.statistikk.testutils.client

import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.DatasetInfo
import com.papsign.ktor.openapigen.model.info.InfoModel
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.TilbakekrevingsbehandlingOppdatertHendelse
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureM2MTokenProvider
import no.nav.aap.komponenter.server.auth.IdentityProvider
import no.nav.aap.komponenter.server.commonKtorModule
import no.nav.aap.motor.Motor
import no.nav.aap.motor.testutil.ManuellMotorImpl
import no.nav.aap.oppgave.statistikk.OppgaveHendelse
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.statistikk.AppConfig
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.IBQYtelsesstatistikkRepository
import no.nav.aap.statistikk.bigquery.SchemaRegistry
import no.nav.aap.statistikk.bigquery.schemaRegistryYtelseStatistikk
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.defaultGatewayProvider
import no.nav.aap.statistikk.jobber.LagreAvklaringsbehovHendelseJobb
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.module
import no.nav.aap.statistikk.postgresRepositoryRegistry
import no.nav.aap.statistikk.startUp
import no.nav.aap.statistikk.testutils.Fakes
import no.nav.aap.statistikk.testutils.fakes.FakeBQYtelseRepository
import no.nav.aap.statistikk.testutils.fakes.FakePdlGateway
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

private val logger = LoggerFactory.getLogger("TestClientHelpers")

fun <E> testKlient(
    transactionExecutor: TransactionExecutor,
    motor: Motor,
    lagreStoppetHendelseJobb: LagreStoppetHendelseJobb,
    jobbAppender: JobbAppender,
    test: TestClient.() -> E,
): E? {
    val res: E?
    System.setProperty("NAIS_CLUSTER_NAME", "LOCAL")

    System.setProperty("enhet.retry.max.retries", "3")
    System.setProperty("enhet.retry.delay.seconds", "1")

    val restClient = RestClient(
        config = ClientConfig(scope = "statisitkk"),
        tokenProvider = AzureM2MTokenProvider,
        responseHandler = DefaultResponseHandler(),
    )
    motor.start()

    val lagreAvklaringsbehovHendelseJobb = LagreAvklaringsbehovHendelseJobb(jobbAppender)

    val server = embeddedServer(Netty, port = 0) {
        commonKtorModule(
            PrometheusProvider.prometheus,
            InfoModel(title = "AAP - Statistikk", version = "0.0.1"),
            identityProvider = IdentityProvider.ENTRA_ID
        )
        module(
            transactionExecutor,
            jobbAppender,
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

        val texasToken = client.post<Unit, Fakes.TestToken>(
            URI.create(requiredConfigForKey("NAIS_TOKEN_ENDPOINT")),
            PostRequest(Unit)
        )


        client.post<StoppetBehandling, Any>(
            URI.create("$url/stoppetBehandling"),
            PostRequest(hendelse, currentToken = OidcToken(texasToken!!.access_token))
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
    bqYtelseRepository: IBQYtelsesstatistikkRepository = FakeBQYtelseRepository(),
    test: TestClient.(ManuellMotorImpl) -> E,
): E {
    lateinit var motor: ManuellMotorImpl

    System.setProperty("NAIS_CLUSTER_NAME", "LOCAL")

    System.setProperty("enhet.retry.max.retries", "1")
    System.setProperty("enhet.retry.delay.seconds", "0")

    val restClient = RestClient(
        config = ClientConfig(scope = "AAP_SCOPES"),
        tokenProvider = AzureM2MTokenProvider,
        responseHandler = DefaultResponseHandler()
    )

    PrometheusProvider.prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val server = embeddedServer(Netty, port = 0) {
        startUp(
            dbConfig,
            defaultGatewayProvider { register<FakePdlGateway>() },
            bqYtelseRepository,
            { ds, gp, jobber ->
                ManuellMotorImpl(ds, jobber, postgresRepositoryRegistry, gp).also { motor = it }
            },

            )
    }.start()

    val port = runBlocking { server.engine.resolvedConnectors().first().port }

    val res = TestClient(restClient, "http://localhost:$port").test(motor)

    server.stop(1000L, 10_000L)

    return res
}

fun postgresTestConfig(): DbConfig {
    val postgres = PostgreSQLContainer("postgres:18")
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
