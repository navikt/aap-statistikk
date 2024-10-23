package no.nav.aap.statistikk

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.schemaRegistry
import no.nav.aap.statistikk.testutils.Fakes
import no.nav.aap.statistikk.testutils.bigQueryContainer
import no.nav.aap.statistikk.testutils.postgresTestConfig
import org.slf4j.LoggerFactory
import java.net.URI

private val logger = LoggerFactory.getLogger("TestApp")

fun main() {
    val azureFake = Fakes.AzureFake(port = 8081)
    azureFake.start()

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
            ), bigQueryClient
        )
    }.start(wait = true)
}
