package no.nav.aap.statistikk

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.aap.statistikk.bigquery.BigQueryConfig

fun main() {
    val pgConfig = postgresTestConfig()
    println("PGCONFIG: $pgConfig")
    val bqConfig: BigQueryConfig = bigQueryContainer()

    embeddedServer(Netty, port = 8080) {
        startUp(pgConfig, bqConfig)
    }.start(wait = true)
}
