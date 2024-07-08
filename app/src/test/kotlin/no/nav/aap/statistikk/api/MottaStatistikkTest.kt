package no.nav.aap.statistikk.api

import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
                setBody(
                    MottaStatistikkDTO(
                        saksNummer = "123",
                        status = "OPPRETTET",
                        behandlingsType = "Førstegangsbehandling"
                    )
                )
            }
            Assertions.assertEquals(HttpStatusCode.Accepted, res.status)
        }

        verify {
            hendelsesRepository.lagreHendelse(
                MottaStatistikkDTO(
                    saksNummer = "123", status = "OPPRETTET",
                    behandlingsType = "Førstegangsbehandling"
                )
            )
        }
    }
}