package no.nav.aap.statistikk

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.mockk
import no.nav.aap.statistikk.aoi.testKlient
import no.nav.aap.statistikk.api.HendelsesRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


class ApplicationTest {
    @Test
    fun testHelloWorld() {
        val hendelsesRepository = mockk<HendelsesRepository>()
        testKlient(hendelsesRepository) { client ->
            val response = client.get("/")
            Assertions.assertEquals(HttpStatusCode.OK, response.status)
            Assertions.assertEquals(response.body() as String, "Hello World!")
        }
    }
}