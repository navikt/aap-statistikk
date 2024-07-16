import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.aap.statistikk.api.bigQueryContainer
import no.nav.aap.statistikk.api.postgresTestConfig
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.startUp

fun main() {
    val pgConfig = postgresTestConfig()
    val bqConfig: BigQueryConfig = bigQueryContainer()

    embeddedServer(Netty, port = 8080) {
        startUp(pgConfig, bqConfig)
    }.start(wait = true)
}
