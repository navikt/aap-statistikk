package no.nav.aap.statistikk

import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.statistikk.api_kontrakt.AvsluttetBehandlingDTO
import no.nav.aap.statistikk.api_kontrakt.MottaStatistikkDTO
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.vilkårsresultat.VilkårsVurderingTabell
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.testutils.BigQuery
import no.nav.aap.statistikk.testutils.Fakes
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.avsluttetBehandlingDTO
import no.nav.aap.statistikk.testutils.behandlingHendelse
import no.nav.aap.statistikk.testutils.testKlientNoInjection
import no.nav.aap.statistikk.testutils.ventPåSvar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.UUID

@Fakes
class IntegrationTest {
    @Test
    fun `test flyt`(
        // TODO, verifiser
        @Postgres dbConfig: DbConfig,
        @BigQuery config: BigQueryConfig,
        @Fakes azureConfig: AzureConfig,
    ) {

        val behandlingReferanse = UUID.randomUUID()
        val saksnummer = "4LFK2S0"
        val hendelse = behandlingHendelse(saksnummer, behandlingReferanse)
        val avsluttetBehandling = avsluttetBehandlingDTO(behandlingReferanse, saksnummer)

        val bqClient = BigQueryClient(config)

        testKlientNoInjection(dbConfig, config, azureConfig) { url, client ->
            client.post<MottaStatistikkDTO, Any>(URI.create("$url/motta"), PostRequest(hendelse))


            client.post<AvsluttetBehandlingDTO, Any>(
                URI.create("$url/avsluttetBehandling"),
                PostRequest(avsluttetBehandling)
            )

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