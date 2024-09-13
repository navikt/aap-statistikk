package no.nav.aap.statistikk

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.aap.statistikk.api_kontrakt.*
import no.nav.aap.statistikk.jobber.LagreAvsluttetBehandlingDTOJobb
import no.nav.aap.statistikk.jobber.LagreAvsluttetBehandlingJobbKonstruktør
import no.nav.aap.statistikk.server.authenticate.AzureConfig
import no.nav.aap.statistikk.testutils.FakeBQRepository
import no.nav.aap.statistikk.testutils.Fakes
import no.nav.aap.statistikk.testutils.MockJobbAppender
import no.nav.aap.statistikk.testutils.TestToken
import no.nav.aap.statistikk.testutils.motorMock
import no.nav.aap.statistikk.testutils.noOpTransactionExecutor
import no.nav.aap.statistikk.testutils.testKlient
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
        val behandlingReferanse = UUID.randomUUID()

        val jobbAppender = MockJobbAppender()

        val response = testKlient(
            noOpTransactionExecutor,
            motor = motorMock(),
            jobbAppender,
            LagreAvsluttetBehandlingDTOJobb(LagreAvsluttetBehandlingJobbKonstruktør(FakeBQRepository())),
            azureConfig,
        ) { client ->
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
                            grunnlag11_19dto = Grunnlag11_19DTO(
                                inntekter = mapOf(),
                                grunnlaget = 6.0,
                                erGjennomsnitt = true,
                                er6GBegrenset = true
                            )
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
            response
        }

        assertThat(response!!.status.isSuccess()).isTrue()
        runBlocking {
            assertThat(response.body<String>()).isEqualTo("{}")
        }

        assertThat(jobbAppender.jobber).hasSize(1)
        assertThat(jobbAppender.jobber.first().type()).isEqualTo("lagreAvsluttetBehandlingDTO")
    }

    @Test
    fun `kan poste ren json`(
        @Fakes azureConfig: AzureConfig,
        @Fakes token: TestToken
    ) {
        val jobbAppender = MockJobbAppender()

        testKlient(
            noOpTransactionExecutor,
            motorMock(),
            jobbAppender,
            LagreAvsluttetBehandlingDTOJobb(LagreAvsluttetBehandlingJobbKonstruktør(FakeBQRepository())),
            azureConfig
        ) { client ->
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

        assertThat(jobbAppender.jobber).hasSize(1)
    }

    @Test
    fun `kan parse json for beregningsgrunnlag`(
        @Fakes azureConfig: AzureConfig,
        @Fakes token: TestToken
    ) {
        @Language("JSON")
        val payload = """{
  "behandlingsReferanse": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "beregningsGrunnlag": {
    "grunnlag11_19dto": null,
    "grunnlagUføre": null,
    "grunnlagYrkesskade": {
      "andelSomIkkeSkyldesYrkesskade": 0,
      "andelSomSkyldesYrkesskade": 0,
      "andelYrkesskade": 0,
      "antattÅrligInntektYrkesskadeTidspunktet": 0,
      "benyttetAndelForYrkesskade": 0,
      "beregningsgrunnlag": {
        "grunnlag11_19dto": null,
        "grunnlagUføre": {
          "grunnlag": {
            "er6GBegrenset": true,
            "erGjennomsnitt": true,
            "grunnlaget": 0,
            "inntekter": {
              "2021": 0,
              "2022": 0,
              "2023": 0
            }
          },
          "grunnlagYtterligereNedsatt": {
            "er6GBegrenset": true,
            "erGjennomsnitt": true,
            "grunnlaget": 0,
            "inntekter": {
              "2021": 0,
              "2022": 0,
              "2023": 0
            }
          },
          "grunnlaget": 4.5,
          "type": "YTTERLIGERE_NEDSATT",
          "uføreInntektIKroner": 0,
          "uføreInntekterFraForegåendeÅr": {
            "2021": 0,
            "2022": 0,
            "2023": 0
          },
          "uføreYtterligereNedsattArbeidsevneÅr": 0,
          "uføregrad": 0
        }
      },
      "grunnlagEtterYrkesskadeFordel": 0,
      "grunnlagForBeregningAvYrkesskadeandel": 0,
      "grunnlaget": 4.5,
      "inkludererUføre": false,
      "terskelverdiForYrkesskade": 0,
      "yrkesskadeTidspunkt": 0,
      "yrkesskadeinntektIG": 0
    }
  },
  "saksnummer": "string",
  "tilkjentYtelse": {
    "perioder": []
  },
  "vilkårsResultat": {
    "typeBehandling": "string",
    "vilkår": []
  }
}"""

        val jobbAppender = MockJobbAppender()

        testKlient(
            noOpTransactionExecutor,
            motorMock(),
            jobbAppender,
            LagreAvsluttetBehandlingDTOJobb(LagreAvsluttetBehandlingJobbKonstruktør(FakeBQRepository())),
            azureConfig
        ) { client ->
            val response = client.post("/avsluttetBehandling") {
                contentType(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer ${token.access_token}")
                }
                setBody(payload)
            }
            Assertions.assertEquals(HttpStatusCode.Accepted, response.status)
        }
        assertThat(jobbAppender.jobber.size).isEqualTo(1)
        assertThat(jobbAppender.jobber.first().type()).isEqualTo("lagreAvsluttetBehandlingDTO")
    }

    @Test
    fun `godtar payload med ukjente felter`(
        @Fakes azureConfig: AzureConfig,
        @Fakes token: TestToken
    ) {
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
  "avklaringsbehov": [],
  "ukjentfelt": "hei"
}"""

        val jobbAppender = MockJobbAppender()

        testKlient(
            noOpTransactionExecutor,
            motorMock(),
            jobbAppender,
            LagreAvsluttetBehandlingDTOJobb(LagreAvsluttetBehandlingJobbKonstruktør(FakeBQRepository())),
            azureConfig
        ) { client ->
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