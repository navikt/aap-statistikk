package no.nav.aap.statistikk

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.server.authenticate.AzureConfig
import java.net.URI

fun main() {
    val azureFake = Fakes.AzureFake(port = 8081)
    azureFake.start()

    val pgConfig = postgresTestConfig()
    println("PGCONFIG: $pgConfig")
    val bqConfig: BigQueryConfig = bigQueryContainer()

    embeddedServer(Netty, port = 8080) {
        startUp(pgConfig, bqConfig, AzureConfig(
            clientId = "tilgang",
            jwks = URI.create("http://localhost:${azureFake.port()}/jwks").toURL(),
            issuer = "tilgang"
        ))
    }.start(wait = true)
}
