package no.nav.aap.statistikk

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.pdl.PdlConfig
import no.nav.aap.statistikk.testutils.Fakes
import no.nav.aap.statistikk.testutils.bigQueryContainer
import no.nav.aap.statistikk.testutils.postgresTestConfig
import no.nav.aap.statistikk.testutils.schemaRegistry
import org.slf4j.LoggerFactory
import java.net.URI

private val logger = LoggerFactory.getLogger("TestApp")

fun main() {
    val azureFake = Fakes.AzureFake(port = 8081)
    azureFake.start()

    val azureConfig = AzureConfig(
        tokenEndpoint = URI.create("http://localhost:${azureFake.port()}/token"),
        clientId = "xxx",
        clientSecret = "xxx",
        jwksUri = "xxx",
        issuer = "xxx"
    )

    System.setProperty("azure.openid.config.token.endpoint", azureConfig.tokenEndpoint.toString())
    System.setProperty("azure.app.client.id", azureConfig.clientId)
    System.setProperty("azure.app.client.secret", azureConfig.clientSecret)
    System.setProperty("azure.openid.config.jwks.uri", azureConfig.jwksUri)
    System.setProperty("azure.openid.config.issuer", azureConfig.issuer)


    val pgConfig = postgresTestConfig()
    logger.info("Postgres Config: $pgConfig")
    val bqConfig: BigQueryConfig = bigQueryContainer()

    val bigQueryClient = BigQueryClient(bqConfig, schemaRegistry)
    // Hack fordi emulator ikke støtter migrering
    schemaRegistry.forEach { (_, schema) ->
        bigQueryClient.create(schema)
    }

    embeddedServer(Netty, port = 8080, watchPaths = listOf("classes")) {
        startUp(
            pgConfig, AzureConfig(
                clientId = "tilgang",
                jwksUri = "http://localhost:${azureFake.port()}/jwks",
                issuer = "tilgang",
                tokenEndpoint = URI.create("http://localhost:${azureFake.port()}/token"),
                clientSecret = "xxx",
            ), bigQueryClient, bigQueryClient, PdlConfig(url = "...", scope = "xxx")
        )
    }.start(wait = true)
}
