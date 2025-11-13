package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.behandling.BehandlingTabell
import no.nav.aap.statistikk.testutils.BigQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BigQueryClientTest {
    @Test
    fun `tabell blir opprettet ved init av klient`(@BigQuery bigQueryConfig: BigQueryConfig) {

        val client =
            BigQueryClient(bigQueryConfig, mapOf("vilkårsvurdering" to BehandlingTabell()))

        assertThat(client.create(BehandlingTabell())).isFalse
    }
}