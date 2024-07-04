package no.nav.aap.statistikk.aoi

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import no.nav.aap.statistikk.DbConfig
import no.nav.aap.statistikk.api.HendelsesRepository
import no.nav.aap.statistikk.module
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.time.Duration
import java.time.temporal.ChronoUnit

fun testKlient(hendelsesRepository: HendelsesRepository, test: suspend (HttpClient) -> Unit) {
    val postgres = postgreSQLContainer()
    val dbConfig = DbConfig(database = "sss", url = postgres.jdbcUrl, password = postgres.password, username = postgres.username)

    testApplication {
        application {
            module(dbConfig, hendelsesRepository)
        }
        val client = client.config {
            install(ContentNegotiation) {
                jackson { }
            }
        }

        test(client)
    }
}

private fun postgreSQLContainer(): PostgreSQLContainer<Nothing> {
    val postgres = PostgreSQLContainer<Nothing>("postgres:15")
    postgres.waitingFor(HostPortWaitStrategy().withStartupTimeout(Duration.of(60L, ChronoUnit.SECONDS)))
    postgres.start()
    return postgres
}