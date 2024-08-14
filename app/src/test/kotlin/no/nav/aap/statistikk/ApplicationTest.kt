package no.nav.aap.statistikk

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.*
import no.nav.aap.statistikk.avsluttetbehandling.api.*
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.beregningsgrunnlag.BeregningsGrunnlagService
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.server.authenticate.AzureConfig
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelseService
import no.nav.aap.statistikk.vilkårsresultat.VilkårsResultatService
import no.nav.aap.statistikk.vilkårsresultat.Vilkårtype
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

@Fakes
class ApplicationTest {
    @Test
    fun `kan parse avsluttet behandling dto og returnerer database-id`(
        @Fakes azureConfig: AzureConfig,
        @Fakes token: TestToken
    ) {
        val hendelsesRepository = mockk<IHendelsesRepository>()
        val vilkårsResultatService = mockk<VilkårsResultatService>()
        val tilkjentYtelseService = mockk<TilkjentYtelseService>()
        val beregningsGrunnlagService = mockk<BeregningsGrunnlagService>()
        val avsluttetBehandlingService =
            AvsluttetBehandlingService(
                vilkårsResultatService,
                tilkjentYtelseService,
                beregningsGrunnlagService
            )

        val behandlingReferanse = UUID.randomUUID()

        every { vilkårsResultatService.mottaVilkårsResultat(any()) } returns 1
        every { tilkjentYtelseService.lagreTilkjentYtelse(any()) } just Runs
        every { beregningsGrunnlagService.mottaBeregningsGrunnlag(any()) } just Runs

        testKlient(hendelsesRepository, avsluttetBehandlingService, azureConfig) { client ->
            val response = client.post("/avsluttetBehandling") {
                headers {
                    append("Authorization", "Bearer ${token.access_token}")
                }
                val vilkårsresultat = VilkårsResultatDTO(
                    typeBehandling = "Førstegangsbehandling", vilkår = listOf(
                        VilkårDTO(
                            vilkårType = Vilkårtype.ALDERSVILKÅRET, listOf(
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
                val vilkårsResultatJson =
                    ObjectMapper().registerModule(JavaTimeModule())
                        .writeValueAsString(vilkårsresultat)


                val tilkjentYtelseDTO = TilkjentYtelseDTO(
                    perioder = listOf(
                        TilkjentYtelsePeriodeDTO(
                            fraDato = LocalDate.now().minusDays(10),
                            tilDato = LocalDate.now(),
                            dagsats = 100.23,
                            gradering = 70.2
                        )
                    )
                )

                val tilkjentYtelseJSON =
                    ObjectMapper().registerModule(JavaTimeModule())
                        .writeValueAsString(tilkjentYtelseDTO)

                val beregningsGrunnlag =
                    ObjectMapper().writeValueAsString(
                        BeregningsgrunnlagDTO(
                            grunnlag = 1337.2,
                            er6GBegrenset = true,
                            grunnlag11_19dto = Grunnlag11_19DTO(inntekter = mapOf())
                        )
                    )

                contentType(ContentType.Application.Json)

                @Language("JSON") val jsonBody = """{
  "saksnummer": "4LENXDC",
  "behandlingsReferanse": "$behandlingReferanse",
  "tilkjentYtelse": $tilkjentYtelseJSON,
  "vilkårsResultat": $vilkårsResultatJson,
  "beregningsGrunnlag": $beregningsGrunnlag
}"""
                setBody(jsonBody)
            }

            assertThat(response.status.isSuccess()).isTrue()
            assertThat(response.body<AvsluttetBehandlingResponsDTO>().id).isEqualTo(143)

            verify(exactly = 1) { tilkjentYtelseService.lagreTilkjentYtelse(any()) }
            verify(exactly = 1) { vilkårsResultatService.mottaVilkårsResultat(any()) }

            checkUnnecessaryStub(tilkjentYtelseService, vilkårsResultatService)
        }
    }

    @Test
    fun `kan poste ren json`(
        @Fakes azureConfig: AzureConfig,
        @Fakes token: TestToken
    ) {
        val hendelsesRepository = mockk<IHendelsesRepository>()
        val avsluttetBehandlingService = mockk<AvsluttetBehandlingService>()
        every { hendelsesRepository.lagreHendelse(any()) } returns 1

        testKlient(hendelsesRepository, avsluttetBehandlingService, azureConfig) { client ->
            @Language("JSON")
            val body =
                """{
  "saksnummer": "123456789",
  "behandlingReferanse": "f14dfc5a-9536-4050-a10b-ebe554ecfdd2",
  "behandlingOpprettetTidspunkt": [
    2024,
    8,
    14,
    11,
    5,
    10,
    343319000
  ],
  "status": "OPPRETTET",
  "behandlingType": "Førstegangsbehandling",
  "ident": "1403199012345",
  "avklaringsbehov": []
}"""
            val response = client.post("/motta") {
                contentType(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer ${token.access_token}")
                }
                setBody(body)
            }
            Assertions.assertEquals(HttpStatusCode.Accepted, response.status)
        }

        verify(exactly = 1) { hendelsesRepository.lagreHendelse(any()) }
    }

    @Test
    fun `godtar payload med ukjente felter`(
        @Fakes azureConfig: AzureConfig,
        @Fakes token: TestToken
    ) {
        val hendelsesRepository = mockk<IHendelsesRepository>()
        val avsluttetBehandlingService = mockk<AvsluttetBehandlingService>()
        every { hendelsesRepository.lagreHendelse(any()) } returns 1

        @Language("JSON")
        val payload =
            """{
  "saksnummer": "123456789",
  "behandlingReferanse": "f14dfc5a-9536-4050-a10b-ebe554ecfdd2",
  "behandlingOpprettetTidspunkt": [
    2024,
    8,
    14,
    11,
    5,
    10,
    343319000
  ],
  "status": "OPPRETTET",
  "behandlingType": "Førstegangsbehandling",
  "ident": "1403199012345",
  "avklaringsbehov": []
}"""

        testKlient(hendelsesRepository, avsluttetBehandlingService, azureConfig) { client ->
            val response = client.post("/motta") {
                contentType(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer ${token.access_token}")
                }
                setBody(payload)
            }
            Assertions.assertEquals(HttpStatusCode.Accepted, response.status)
        }
    }
}