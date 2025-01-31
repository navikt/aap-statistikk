package no.nav.aap.statistikk

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.schemaRegistry
import no.nav.aap.statistikk.testutils.Fakes
import no.nav.aap.statistikk.testutils.MockJobbAppender
import no.nav.aap.statistikk.testutils.bigQueryContainer
import no.nav.aap.statistikk.testutils.noOpTransactionExecutor
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.FileWriter
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlin.test.fail

private val logger = LoggerFactory.getLogger("TestApp")

fun main() {
    val azureFake = Fakes.AzureFake(port = 8081)
    azureFake.start()

    val azureConfig = AzureConfig(
        tokenEndpoint = URI.create("http://localhost:${azureFake.port()}/token"),
        clientId = "xxx",
        clientSecret = "xxx",
        jwksUri = "http://localhost:${azureFake.port()}/token",
        issuer = "xxx"
    )

    System.setProperty("azure.openid.config.token.endpoint", azureConfig.tokenEndpoint.toString())
    System.setProperty("azure.app.client.id", azureConfig.clientId)
    System.setProperty("azure.app.client.secret", azureConfig.clientSecret)
    System.setProperty("azure.openid.config.jwks.uri", azureConfig.jwksUri)
    System.setProperty("azure.openid.config.issuer", azureConfig.issuer)


//    val pgConfig = postgresTestConfig()
//    logger.info("Postgres Config: $pgConfig")
    val bqConfig: BigQueryConfig = bigQueryContainer()

    val bigQueryClient = BigQueryClient(bqConfig, schemaRegistry)
    // Hack fordi emulator ikke støtter migrering
    schemaRegistry.forEach { (_, schema) ->
        bigQueryClient.create(schema)
    }

//    val server = embeddedServer(Netty, port = 8080, watchPaths = listOf("classes")) {
//        startUp(
//            pgConfig, AzureConfig(
//                clientId = "tilgang",
//                jwksUri = "http://localhost:${azureFake.port()}/jwks",
//                issuer = "tilgang",
//                tokenEndpoint = URI.create("http://localhost:${azureFake.port()}/token"),
//                clientSecret = "xxx",
//            ), bigQueryClient, PdlConfig(url = "...", scope = "xxx")
//        )
//    }.start()

    val server = embeddedServer(Netty, port = 8080, watchPaths = listOf("classes")) {
        module(
            transactionExecutor = noOpTransactionExecutor,
            motor = mockk(relaxed = true),
            jobbAppender = MockJobbAppender(),
            azureConfig = azureConfig,
            motorApiCallback = { },
            lagreStoppetHendelseJobb = mockk(),
            lagreOppgaveHendelseJobb = mockk(),
            lagrePostmottakHendelseJobb = mockk(),
            prometheusMeterRegistry = SimpleMeterRegistry()
        )
    }.start()

    val restClient = RestClient(
        config = ClientConfig(scope = "AAP_SCOPES"),
        tokenProvider = ClientCredentialsTokenProvider,
        responseHandler = DefaultResponseHandler()
    )

    val resp = restClient.get<String>(
        URI.create("http://localhost:8080/openapi.json"),
        request = GetRequest(),
        mapper = { body, _ -> String(body.readAllBytes(), StandardCharsets.UTF_8) }
    )!!

    try {
        val writer = BufferedWriter(FileWriter("openapi.json"))
        writer.write(resp)

        writer.close()
    } catch (_: Exception) {
        fail()
    }

    server.stop()
}
