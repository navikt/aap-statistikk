package no.nav.aap.statistikk.api

import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.aap.statistikk.AzureTokenGen
import no.nav.aap.statistikk.Fakes
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.hendelser.api.MottaStatistikkDTO
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.testKlient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MottaStatistikkTest {
    companion object {
        private val fakes = Fakes()

        @AfterAll
        @JvmStatic
        internal fun afterAll() {
            fakes.close()
        }
    }

    @Test
    fun `hendelse blir lagret i repository`() {
        val hendelsesRepository = mockk<IHendelsesRepository>()
        val avsluttetBehandlingService = mockk<AvsluttetBehandlingService>()
        every { hendelsesRepository.lagreHendelse(any()) } returns Unit

        val token = AzureTokenGen("tilgang", "tilgang").generate()

        testKlient(hendelsesRepository, avsluttetBehandlingService) { client ->
            val res = client.post("/motta") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
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