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
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.VilkårsVurderingTabell
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.server.authenticate.AzureConfig
import no.nav.aap.statistikk.testutils.BigQuery
import no.nav.aap.statistikk.testutils.Fakes
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.TestToken
import no.nav.aap.statistikk.testutils.avsluttetBehandlingDTO
import no.nav.aap.statistikk.testutils.testKlientNoInjection
import no.nav.aap.statistikk.testutils.ventPåSvar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Fakes
class IntegrationTest {
    @Test
    fun `test flyt`(
        // TODO, verifiser
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

        val avsluttetBehandling = avsluttetBehandlingDTO(behandlingReferanse, saksnummer)

        val bqClient = BigQueryClient(config)

        testKlientNoInjection(dbConfig, config, azureConfig) { client ->
            client.post("/motta") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${token.access_token}")
                }
                setBody(hendelse)
            }

            client.post("/avsluttetBehandling") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${token.access_token}")
                }
                setBody(avsluttetBehandling)
            }

            val bigQueryRespons =
                ventPåSvar({ bqClient.read(VilkårsVurderingTabell()) }, { t -> t.isNotEmpty() })

            assertThat(bigQueryRespons).hasSize(1)
        }
    }

}