package no.nav.aap.statistikk.api

import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.aap.statistikk.aoi.testKlient
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MottaStatistikkTest {
    @Test
    fun `hendelse blir lagret i repository`() {
        val hendelsesRepository = mockk<HendelsesRepository>()
        every { hendelsesRepository.lagreHendelse(any()) } returns Unit

        testKlient(hendelsesRepository) { client ->
            val res = client.post("/motta") {
                contentType(ContentType.Application.Json)
                setBody(MottaStatistikkDTO(sakId = "123"))
            }
            Assertions.assertEquals(HttpStatusCode.Accepted, res.status)
        }

        verify { hendelsesRepository.lagreHendelse(MottaStatistikkDTO(sakId = "123")) }
    }
}