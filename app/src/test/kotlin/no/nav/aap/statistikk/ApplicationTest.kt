package no.nav.aap.statistikk

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }

        val response = client.get("/")

        Assertions.assertEquals(HttpStatusCode.OK, response.status)
        Assertions.assertEquals(response.body() as String, "Hello World!")
    }
}