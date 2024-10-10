package no.nav.aap.statistikk.behandling

import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.schemaRegistry
import no.nav.aap.statistikk.testutils.BigQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class BehandlingTabellTest {
    @Test
    fun `sette inn og ta ut`(@BigQuery bigQuery: BigQueryConfig) {
        val client = BigQueryClient(bigQuery, schemaRegistry)

        val tabell = BehandlingTabell()

        val referanse = UUID.randomUUID()

        client.insert(
            tabell, BQYtelseBehandling(
                referanse = referanse,
                brukerFnr = "2902198512345",
                behandlingsType = TypeBehandling.Førstegangsbehandling
            )
        )

        val read = client.read(tabell)

        assertThat(read.size).isEqualTo(1)
        assertThat(read.first().referanse).isEqualTo(referanse)
        assertThat(read.first().brukerFnr).isEqualTo("2902198512345")
        assertThat(read.first().behandlingsType).isEqualTo(TypeBehandling.Førstegangsbehandling)
    }
}