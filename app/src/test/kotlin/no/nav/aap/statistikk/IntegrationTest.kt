package no.nav.aap.statistikk

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.statistikk.api_kontrakt.AvsluttetBehandlingDTO
import no.nav.aap.statistikk.api_kontrakt.SakStatus
import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.schemaRegistry
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.sak.SakTabell
import no.nav.aap.statistikk.testutils.*
import no.nav.aap.statistikk.vilkårsresultat.VilkårsVurderingTabell
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.*
import javax.sql.DataSource

@Fakes
class IntegrationTest {
    @Test
    fun `test flyt`(
        @Postgres dbConfig: DbConfig,
        @Postgres dataSource: DataSource,
        @BigQuery config: BigQueryConfig,
        @Fakes azureConfig: AzureConfig,
    ) {

        val behandlingReferanse = UUID.randomUUID()
        val saksnummer = "4LFK2S0"
        val hendelse = behandlingHendelse(saksnummer, behandlingReferanse)
        val avsluttetBehandling = avsluttetBehandlingDTO(behandlingReferanse, saksnummer)

        val bigQueryClient = BigQueryClient(config, mapOf())

        // Hack fordi emulator ikke støtter migrering
        schemaRegistry.forEach { (_, schema) ->
            bigQueryClient.create(schema)
        }

        testKlientNoInjection(
            dbConfig,
            azureConfig,
            bigQueryClient
        ) { url, client ->
            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"),
                PostRequest(hendelse)
            )

            ventPåSvar(
                { dataSource.transaction { BehandlingRepository(it).hent(behandlingReferanse) } },
                { it != null }
            )

            val bqSaker = ventPåSvar({ bigQueryClient.read(SakTabell()) },
                { t -> t !== null && t.isNotEmpty() })
            assertThat(bqSaker!!.first().sekvensNummer).isEqualTo(1)

            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"),
                PostRequest(hendelse.copy(sakStatus = SakStatus.AVSLUTTET))
            )

            // Sekvensnummer økes med 1 med ny info på sak
            val bqSaker2 = ventPåSvar({ bigQueryClient.read(SakTabell()) },
                { t -> t !== null && t.isNotEmpty() && t.size > 1 })
            assertThat(bqSaker2!![1].sekvensNummer).isEqualTo(2)

            client.post<AvsluttetBehandlingDTO, Any>(
                URI.create("$url/avsluttetBehandling"),
                PostRequest(avsluttetBehandling)
            )

            val bigQueryRespons =
                ventPåSvar(
                    { bigQueryClient.read(VilkårsVurderingTabell()) },
                    { t -> t !== null && t.isNotEmpty() })

            assertThat(bigQueryRespons).hasSize(1)
            val vilkårsVurderingRad = bigQueryRespons!!.first()

            assertThat(vilkårsVurderingRad.behandlingsReferanse).isEqualTo(behandlingReferanse)
            assertThat(vilkårsVurderingRad.saksnummer).isEqualTo(saksnummer)

            val sakRespons = ventPåSvar({ bigQueryClient.read(SakTabell()) },
                { t -> t !== null && t.isNotEmpty() })

            assertThat(sakRespons).hasSize(2)
        }
    }
}