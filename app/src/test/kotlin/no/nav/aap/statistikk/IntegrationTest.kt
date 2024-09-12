package no.nav.aap.statistikk

import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import no.nav.aap.statistikk.api_kontrakt.AvklaringsbehovHendelse
import no.nav.aap.statistikk.api_kontrakt.AvsluttetBehandlingDTO
import no.nav.aap.statistikk.api_kontrakt.BehovType
import no.nav.aap.statistikk.api_kontrakt.BeregningsgrunnlagDTO
import no.nav.aap.statistikk.api_kontrakt.Definisjon
import no.nav.aap.statistikk.api_kontrakt.Endring
import no.nav.aap.statistikk.api_kontrakt.EndringStatus
import no.nav.aap.statistikk.api_kontrakt.Grunnlag11_19DTO
import no.nav.aap.statistikk.api_kontrakt.GrunnlagYrkesskadeDTO
import no.nav.aap.statistikk.api_kontrakt.MottaStatistikkDTO
import no.nav.aap.statistikk.api_kontrakt.TilkjentYtelseDTO
import no.nav.aap.statistikk.api_kontrakt.TilkjentYtelsePeriodeDTO
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.api_kontrakt.Utfall
import no.nav.aap.statistikk.api_kontrakt.VilkårDTO
import no.nav.aap.statistikk.api_kontrakt.VilkårsPeriodeDTO
import no.nav.aap.statistikk.api_kontrakt.VilkårsResultatDTO
import no.nav.aap.statistikk.api_kontrakt.Vilkårtype
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.server.authenticate.AzureConfig
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Fakes
class IntegrationTest {
    @Test
    fun `ffff ff`(
        @Postgres dbConfig: DbConfig,
        @BigQuery config: BigQueryConfig,
        @Fakes token: TestToken,
        @Fakes azureConfig: AzureConfig,
    ) {

        val behandlingReferanse = UUID.randomUUID()
        val saksnummer = "4LFK2S0"
        val hendelse = MottaStatistikkDTO(
            saksnummer = saksnummer,
            behandlingReferanse = behandlingReferanse,
            status = "UTREDES",
            behandlingType = TypeBehandling.Førstegangsbehandling,
            ident = "14890097570",
            avklaringsbehov = listOf(
                AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = "5003",
                        behovType = BehovType.valueOf("MANUELT_PÅKREVD"),
                        løsesISteg = "AVKLAR_SYKDOM"
                    ),
                    status = EndringStatus.valueOf("SENDT_TILBAKE_FRA_KVALITETSSIKRER"),
                    endringer = listOf(
                        Endring(
                            status = EndringStatus.valueOf("OPPRETTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T10:35:34.842"),
                            frist = null,
                            endretAv = "Kelvin"
                        ),
                        Endring(
                            status = EndringStatus.valueOf("AVSLUTTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:50:50.217"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                ),
                AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = "5006",
                        behovType = BehovType.valueOf("MANUELT_PÅKREVD"),
                        løsesISteg = "VURDER_BISTANDSBEHOV"
                    ),
                    status = EndringStatus.valueOf("SENDT_TILBAKE_FRA_KVALITETSSIKRER"),
                    endringer = listOf(
                        Endring(
                            status = EndringStatus.valueOf("OPPRETTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:50:52.049"),
                            frist = null,
                            endretAv = "Kelvin"
                        ),
                        Endring(
                            status = EndringStatus.valueOf("AVSLUTTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:51:16.176"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                ),
                AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = "5097",
                        behovType = BehovType.valueOf("MANUELT_PÅKREVD"),
                        løsesISteg = "KVALITETSSIKRING"
                    ),
                    status = EndringStatus.valueOf("AVSLUTTET"),
                    endringer = listOf(
                        Endring(
                            status = EndringStatus.valueOf("OPPRETTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:51:17.231"),
                            frist = null,
                            endretAv = "Kelvin"
                        ),
                        Endring(
                            status = EndringStatus.valueOf("AVSLUTTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:54:22.268"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                )
            ),
            behandlingOpprettetTidspunkt = LocalDateTime.parse("2024-08-14T10:35:33.595")
        )

        val avsluttetBehandling = AvsluttetBehandlingDTO(
            behandlingsReferanse = behandlingReferanse,
            saksnummer = saksnummer,
            tilkjentYtelse = TilkjentYtelseDTO(
                perioder = listOf(
                    TilkjentYtelsePeriodeDTO(
                        fraDato = LocalDate.now().minusYears(1),
                        tilDato = LocalDate.now().plusDays(1),
                        dagsats = 1337.420,
                        gradering = 90.0
                    ),
                    TilkjentYtelsePeriodeDTO(
                        fraDato = LocalDate.now().minusYears(3),
                        tilDato = LocalDate.now().minusYears(2),
                        dagsats = 1234.0,
                        gradering = 45.0
                    )
                )
            ),
            vilkårsResultat = VilkårsResultatDTO(
                typeBehandling = "førstegangsbehandling",
                vilkår = listOf(
                    VilkårDTO(
                        vilkårType = Vilkårtype.ALDERSVILKÅRET, perioder = listOf(
                            VilkårsPeriodeDTO(
                                fraDato = LocalDate.now().minusYears(2),
                                tilDato = LocalDate.now().plusDays(3),
                                manuellVurdering = false,
                                utfall = Utfall.OPPFYLT
                            )
                        )
                    )
                )
            ),
            beregningsGrunnlag = BeregningsgrunnlagDTO(
                grunnlagYrkesskade = GrunnlagYrkesskadeDTO(
                    grunnlaget = BigDecimal(25000.0),
                    inkludererUføre = false,
                    beregningsgrunnlag = BeregningsgrunnlagDTO(
                        grunnlag11_19dto = Grunnlag11_19DTO(
                            inntekter = mapOf("2019" to 25000.0, "2020" to 26000.0),
                            grunnlaget = 20000.0,
                            er6GBegrenset = false,
                            erGjennomsnitt = true,
                        )
                    ),
                    terskelverdiForYrkesskade = 70,
                    andelSomSkyldesYrkesskade = BigDecimal(30),
                    andelYrkesskade = 25,
                    benyttetAndelForYrkesskade = 20,
                    andelSomIkkeSkyldesYrkesskade = BigDecimal(40),
                    antattÅrligInntektYrkesskadeTidspunktet = BigDecimal(25000),
                    yrkesskadeTidspunkt = 2018,
                    grunnlagForBeregningAvYrkesskadeandel = BigDecimal(25000),
                    yrkesskadeinntektIG = BigDecimal(6),
                    grunnlagEtterYrkesskadeFordel = BigDecimal(25000)
                ),
            ),
        )
        testKlientNoInjection(dbConfig, config, azureConfig) { client ->
            val res = client.post("/motta") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${token.access_token}")
                }
                setBody(hendelse)
            }

            val res2 = client.post("/avsluttetBehandling") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${token.access_token}")
                }
                setBody(avsluttetBehandling)
            }


            Thread.sleep(1000)

        }
    }

}