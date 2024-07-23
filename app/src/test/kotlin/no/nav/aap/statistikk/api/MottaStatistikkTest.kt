package no.nav.aap.statistikk.api

import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.*
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.hendelser.api.MottaStatistikkDTO
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.testKlient
import no.nav.aap.statistikk.vilkårsresultat.service.VilkårsResultatService
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MottaStatistikkTest {
    @Test
    fun `hendelse blir lagret i repository`() {
        val hendelsesRepository = mockk<IHendelsesRepository>()
        val vilkårsResultatService = mockk<VilkårsResultatService>()
        val avsluttetBehandlingService = mockk<AvsluttetBehandlingService>()
        every { hendelsesRepository.lagreHendelse(any()) } returns Unit

        testKlient(hendelsesRepository, vilkårsResultatService, avsluttetBehandlingService) { client ->
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

    @Test
    fun `kan motta json av vilkårsresultat`() {
        @Language("json")
        val vilkårsResultatJSON = """{
  "typeBehandling" : "Førstegangsbehandling",
  "vilkår" : [ {
    "vilkårType" : "MEDLEMSKAP",
    "perioder" : [ {
      "fraDato" : "2024-07-09",
      "tilDato" : "2024-07-11",
      "utfall" : "OPPFYLT",
      "manuellVurdering" : false,
      "innvilgelsesårsak" : null,
      "avslagsårsak" : null
    } ]
  } ]
}"""

        val hendelsesRepository = mockk<IHendelsesRepository>()
        val vilkårsResultatService = mockk<VilkårsResultatService>()
        val avsluttetBehandlingService = mockk<AvsluttetBehandlingService>()

        every { vilkårsResultatService.mottaVilkårsResultat(any()) } returns 1

        testKlient(hendelsesRepository, vilkårsResultatService, avsluttetBehandlingService) { client ->
            val res = client.post("/vilkarsresultat") {
                contentType(ContentType.Application.Json)
                setBody(
                    vilkårsResultatJSON
                )
            }
            Assertions.assertEquals(HttpStatusCode.Accepted, res.status)
        }
    }
}