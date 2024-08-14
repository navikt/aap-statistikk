package no.nav.aap.statistikk.api

import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.aap.statistikk.Fakes
import no.nav.aap.statistikk.TestToken
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.hendelser.api.MottaStatistikkDTO
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.server.authenticate.AzureConfig
import no.nav.aap.statistikk.testKlient
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

@Fakes
class MottaStatistikkTest {
    @Test
    fun `hendelse blir lagret i repository`(
        @Fakes azureConfig: AzureConfig,
        @Fakes token: TestToken
    ) {
        val hendelsesRepository = mockk<IHendelsesRepository>()
        val avsluttetBehandlingService = mockk<AvsluttetBehandlingService>()
        every { hendelsesRepository.lagreHendelse(any()) } returns 1

        val behandlingReferanse = UUID.randomUUID()
        val behandlingOpprettetTidspunkt = LocalDateTime.now()
        testKlient(hendelsesRepository, avsluttetBehandlingService, azureConfig) { client ->
            val res = client.post("/motta") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${token.access_token}")
                }
                setBody(
                    MottaStatistikkDTO(
                        saksnummer = "123",
                        status = "OPPRETTET",
                        behandlingType = "Førstegangsbehandling",
                        ident = "0",
                        behandlingReferanse = behandlingReferanse,
                        behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt
                    )
                )
            }
            Assertions.assertEquals(HttpStatusCode.Accepted, res.status)
        }

        verify {
            hendelsesRepository.lagreHendelse(
                MottaStatistikkDTO(
                    saksnummer = "123",
                    status = "OPPRETTET",
                    behandlingType = "Førstegangsbehandling",
                    ident = "0",
                    behandlingReferanse = behandlingReferanse,
                    behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt
                )
            )
        }
    }
}