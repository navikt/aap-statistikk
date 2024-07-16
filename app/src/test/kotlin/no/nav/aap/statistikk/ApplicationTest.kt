package no.nav.aap.statistikk

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import no.nav.aap.statistikk.api.testKlient
import no.nav.aap.statistikk.api.testKlientMedTestContainer
import no.nav.aap.statistikk.db.Flyway
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.vilkårsresultat.api.*
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import no.nav.aap.statistikk.vilkårsresultat.service.VilkårsResultatService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDate

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
    fun `poste vilkårsresultat`() {
        testKlientMedTestContainer { (client, dbConfig, _) ->

            val vilkårsresultat =
                VilkårsResultatDTO(
                    typeBehandling = "Førstegangsbehandling", saksnummer = "ABC", vilkår = listOf(
                        VilkårDTO(
                            vilkårType = "ALDERS",
                            listOf(
                                VilkårsPeriodeDTO(
                                    fraDato = LocalDate.now().minusDays(10),
                                    tilDato = LocalDate.now().minusDays(0),
                                    utfall = Utfall.IKKE_OPPFYLT,
                                    manuellVurdering = true
                                )
                            )
                        ),
                    )
                )

            // konverter til json med jackson
            val json = ObjectMapper()
                .registerModule(JavaTimeModule())
                .writeValueAsString(vilkårsresultat)

            val response = client.post("/vilkarsresultat") {
                contentType(ContentType.Application.Json)
                setBody(json)
            }

            val resp = response.body<VilkårsResultatResponsDTO>()

            val dataSource = Flyway().createAndMigrateDataSource(dbConfig)

            val uthentet = VilkårsresultatRepository(dataSource).hentVilkårsResultat(resp.id)

            assertThat(response.status).isEqualTo(HttpStatusCode.Accepted)
            assertThat(uthentet!!.saksnummer).isEqualTo("ABC")
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