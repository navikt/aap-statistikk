package no.nav.aap.statistikk

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import no.nav.aap.statistikk.api.testKlient
import no.nav.aap.statistikk.api.testKlientMedTestContainer
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.vilkårsresultat.service.VilkårsResultatService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


class ApplicationTest {
    @Test
    fun testHelloWorld() {
        val hendelsesRepository = mockk<IHendelsesRepository>()
        val vilkårsResultatService = mockk<VilkårsResultatService>()
        testKlient(hendelsesRepository, vilkårsResultatService) { client ->
            val response = client.get("/")
            Assertions.assertEquals(HttpStatusCode.OK, response.status)
            Assertions.assertEquals(response.body() as String, "Hello World!")
        }
    }

    @Test
    fun `hello world med testcontainer`() {
        testKlientMedTestContainer { client ->
            val response = client.get("/openapi.json")
            Assertions.assertEquals(HttpStatusCode.OK, response.status)
            assertThat(response.body<String>()).isNotEmpty()
        }
    }

    @Test
    fun `kan poste ren json`() {
        val hendelsesRepository = mockk<IHendelsesRepository>()
        val vilkårsResultatService = mockk<VilkårsResultatService>()
        every { hendelsesRepository.lagreHendelse(any()) } returns Unit

        testKlient(hendelsesRepository, vilkårsResultatService) { client ->
            val response = client.post("/motta") {
                contentType(ContentType.Application.Json)
                setBody("""{"saksnummer": "123456789", "status": "OPPRETTET", "behandlingType": "Revurdering"}""")
            }
            Assertions.assertEquals(HttpStatusCode.Accepted, response.status)
        }
    }
}