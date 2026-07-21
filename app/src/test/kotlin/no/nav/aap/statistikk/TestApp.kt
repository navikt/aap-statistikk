package no.nav.aap.statistikk

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.aap.statistikk.bigquery.BQYtelseRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.testutils.Fakes
import no.nav.aap.statistikk.testutils.client.bigQueryContainer
import no.nav.aap.statistikk.testutils.client.postgresTestConfig
import no.nav.aap.statistikk.testutils.client.schemaRegistry
import org.slf4j.LoggerFactory
import java.util.*

private val logger = LoggerFactory.getLogger("TestApp")

fun main() {
    val texasFake = Fakes.TexasFake(port = 8081)
    texasFake.start()

    System.setProperty("NAIS_TOKEN_ENDPOINT", "http://localhost:8081/token")
    System.setProperty("NAIS_TOKEN_EXCHANGE_ENDPOINT", "http://localhost:8081/token/exchange")
    System.setProperty("NAIS_TOKEN_INTROSPECTION_ENDPOINT", "http://localhost:8081/introspect")

    val randomUUID = UUID.randomUUID()
    System.setProperty("integrasjon.postmottak.azp", randomUUID.toString())
    System.setProperty("integrasjon.oppgave.azp", randomUUID.toString())
    System.setProperty("integrasjon.behandlingsflyt.azp", randomUUID.toString())


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
            pgConfig, defaultGatewayProvider(),
            bqYtelseRepository = BQYtelseRepository(bigQueryClient)
        )
    }.start(wait = true)
}
