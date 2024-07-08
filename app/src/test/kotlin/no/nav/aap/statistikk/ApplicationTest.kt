package no.nav.aap.statistikk

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import no.nav.aap.statistikk.api.testKlient
import no.nav.aap.statistikk.api.IHendelsesRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


class ApplicationTest {
    @Test
    fun testHelloWorld() {
        val IHendelsesRepository = mockk<IHendelsesRepository>()
        testKlient(IHendelsesRepository) { client ->
            val response = client.get("/")
            Assertions.assertEquals(HttpStatusCode.OK, response.status)
            Assertions.assertEquals(response.body() as String, "Hello World!")
        }
    }

    @Test
    fun `kan poste ren json`() {
        val IHendelsesRepository = mockk<IHendelsesRepository>()
        every { IHendelsesRepository.lagreHendelse(any()) } returns Unit

        testKlient(IHendelsesRepository) { client ->
            val response = client.post("/motta") {
                contentType(ContentType.Application.Json)
                setBody("""{"saksnummer": "123456789", "status": "OPPRETTET", "behandlingType": "Revurdering"}""")
            }
            Assertions.assertEquals(HttpStatusCode.Accepted, response.status)
        }
    }
}