package no.nav.aap.statistikk

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.ContentType
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.statistikk.api_kontrakt.*
import no.nav.aap.statistikk.jobber.LagreAvsluttetBehandlingDTOJobb
import no.nav.aap.statistikk.jobber.LagreAvsluttetBehandlingJobbKonstruktør
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.testutils.*
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.LocalDate
import java.util.*

@Fakes
class ApplicationTest {
    @Test
    fun `kan parse avsluttet behandling dto og returnerer database-id`(
        @Fakes azureConfig: AzureConfig
    ) {
        val behandlingReferanse = UUID.randomUUID()

        val jobbAppender = MockJobbAppender()

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

        @Language("JSON") val jsonBody = """{
  "saksnummer": "4LENXDC",
  "behandlingsReferanse": "$behandlingReferanse",
  "tilkjentYtelse": $tilkjentYtelseJSON,
  "vilkårsResultat": $vilkårsResultatJson,
  "beregningsGrunnlag": $beregningsGrunnlag
}"""

        val bqRepository = FakeBQRepository()
        val meterRegistry = SimpleMeterRegistry()

        val avsluttetBehandlingCounter = meterRegistry.avsluttetBehandlingLagret()
        val avsluttetBehandlingDtoLagretCounter = meterRegistry.avsluttetBehandlingDtoLagret()
        val stoppetHendelseLagretCounter = meterRegistry.hendelseLagret()

        val response = testKlient(
            noOpTransactionExecutor,
            motor = motorMock(),
            jobbAppender,
            LagreAvsluttetBehandlingDTOJobb(
                LagreAvsluttetBehandlingJobbKonstruktør(
                    bqRepository,
                    avsluttetBehandlingCounter
                ),
                avsluttetBehandlingDtoLagretCounter
            ),
            azureConfig,
            LagreStoppetHendelseJobb(
                bqRepository, stoppetHendelseLagretCounter,
                bigQueryKvitteringRepository = { FakeBigQueryKvitteringRepository() }
            ),
        ) { url, client ->

            val respons = client.post<AvsluttetBehandlingDTO, LinkedHashMap<String, String>>(
                URI.create("$url/avsluttetBehandling"), PostRequest(
                    body = DefaultJsonMapper.fromJson<AvsluttetBehandlingDTO>(jsonBody)
                )
            )
            respons
        }

        assertThat(response!!.size).isEqualTo(0)

        assertThat(jobbAppender.jobber).hasSize(1)
        assertThat(jobbAppender.jobber.first().type()).isEqualTo("lagreAvsluttetBehandlingDTO")
    }

    @Test
    fun `kan poste mottastatistikk, og jobb blir opprettet`(
        @Fakes azureConfig: AzureConfig
    ) {
        val jobbAppender = MockJobbAppender()
        val bqRepository = FakeBQRepository()
        val meterRegistry = SimpleMeterRegistry()

        testKlient(
            noOpTransactionExecutor,
            motorMock(),
            jobbAppender,
            LagreAvsluttetBehandlingDTOJobb(
                LagreAvsluttetBehandlingJobbKonstruktør(
                    bqRepository,
                    meterRegistry.avsluttetBehandlingLagret()
                ),
                meterRegistry.avsluttetBehandlingDtoLagret()
            ),
            azureConfig,
            LagreStoppetHendelseJobb(
                bqRepository, meterRegistry.hendelseLagret(),
                bigQueryKvitteringRepository = { FakeBigQueryKvitteringRepository() }
            ),
        ) { url, client ->
            @Language("JSON")
            val body =
                """{
  "saksnummer": "123456789",
  "sakStatus": "OPPRETTET",
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
  "versjon": "UKJENT",
  "mottattTid": [
    2024,
    8,
    14,
    11,
    5,
    10,
    343319000
  ]
}"""

            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"),
                PostRequest(
                    DefaultJsonMapper.fromJson(body),
                    contentType = ContentType.APPLICATION_JSON
                )
            )

        }

        assertThat(jobbAppender.jobber).hasSize(1)
    }

    @Test
    fun `kan parse json for beregningsgrunnlag`(
        @Fakes azureConfig: AzureConfig
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
        val meterRegistry = SimpleMeterRegistry()
        val avsluttetBehandlingCounter = meterRegistry.avsluttetBehandlingLagret()
        val avsluttetBehandlingDtoLagretCounter = meterRegistry.avsluttetBehandlingDtoLagret()
        val stoppetHendelseLagretCounter = meterRegistry.hendelseLagret()

        testKlient(
            noOpTransactionExecutor,
            motorMock(),
            jobbAppender,
            LagreAvsluttetBehandlingDTOJobb(
                LagreAvsluttetBehandlingJobbKonstruktør(
                    FakeBQRepository(),
                    avsluttetBehandlingCounter
                ),
                avsluttetBehandlingDtoLagretCounter
            ),
            azureConfig,
            LagreStoppetHendelseJobb(
                FakeBQRepository(),
                stoppetHendelseLagretCounter,
                bigQueryKvitteringRepository = { FakeBigQueryKvitteringRepository() }
            )
        ) { url, client ->
            client.post<AvsluttetBehandlingDTO, String>(
                URI.create("$url/avsluttetBehandling"),
                PostRequest(
                    DefaultJsonMapper.fromJson(payload),
                    contentType = ContentType.APPLICATION_JSON
                )
            ) { st, s -> st.toString() }
        }
        assertThat(jobbAppender.jobber.size).isEqualTo(1)
        assertThat(jobbAppender.jobber.first().type()).isEqualTo("lagreAvsluttetBehandlingDTO")
    }

    @Test
    fun `godtar payload med ukjente felter`(
        @Fakes azureConfig: AzureConfig
    ) {
        @Language("JSON")
        val payload =
            """{
  "saksnummer": "123456789",
  "sakStatus": "OPPRETTET",
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
  "versjon": "UKJENT",
  "mottattTid": [
    2024,
    8,
    14,
    11,
    5,
    10,
    343319000
  ],
  "ukjentfelt": "hei"
}"""

        val jobbAppender = MockJobbAppender()
        val bqRepository = FakeBQRepository()
        val meterRegistry = SimpleMeterRegistry()

        val avsluttetBehandlingDtoLagretCounter = meterRegistry.avsluttetBehandlingDtoLagret()
        val avsluttetBehandlingCounter = meterRegistry.avsluttetBehandlingLagret()
        val stoppetHendelseLagretCounter = meterRegistry.hendelseLagret()

        testKlient(
            noOpTransactionExecutor,
            motorMock(),
            jobbAppender,
            LagreAvsluttetBehandlingDTOJobb(
                LagreAvsluttetBehandlingJobbKonstruktør(
                    bqRepository,
                    avsluttetBehandlingCounter
                ),
                avsluttetBehandlingDtoLagretCounter
            ),
            azureConfig,
            LagreStoppetHendelseJobb(
                bqRepository, stoppetHendelseLagretCounter,
                bigQueryKvitteringRepository = { FakeBigQueryKvitteringRepository() }
            ),
        ) { url, client ->
            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"), PostRequest(
                    DefaultJsonMapper.fromJson<StoppetBehandling>(payload),
                    additionalHeaders = listOf(
                        Header("Accept", "application/json"),
                        Header("Content-Type", "application/json")
                    )
                )
            )
        }
    }
}