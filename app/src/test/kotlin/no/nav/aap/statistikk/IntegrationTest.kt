package no.nav.aap.statistikk

import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
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
import no.nav.aap.statistikk.testutils.behandlingHendelse
import no.nav.aap.statistikk.testutils.testKlientNoInjection
import no.nav.aap.statistikk.testutils.ventPåSvar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
        val hendelse = behandlingHendelse(saksnummer, behandlingReferanse)
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
            val vilkårsVurderingRad = bigQueryRespons!!.first()

            assertThat(vilkårsVurderingRad.vilkår.size).isEqualTo(avsluttetBehandling.vilkårsResultat.vilkår.size)
            assertThat(vilkårsVurderingRad.behandlingsReferanse).isEqualTo(behandlingReferanse)
            assertThat(vilkårsVurderingRad.saksnummer).isEqualTo(saksnummer)
        }
    }

}