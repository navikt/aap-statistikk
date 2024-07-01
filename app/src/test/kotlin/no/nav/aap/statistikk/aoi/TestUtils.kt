package no.nav.aap.statistikk.aoi

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.aap.statistikk.api.HendelsesRepository
import no.nav.aap.statistikk.module

fun testKlient(hendelsesRepository: HendelsesRepository, test: suspend (HttpClient) -> Unit) {
    testApplication {
        application {
            module(hendelsesRepository)
        }
        val client = client.config {
            install(ContentNegotiation) {
                jackson { }
            }
        }

        test(client)
    }
}