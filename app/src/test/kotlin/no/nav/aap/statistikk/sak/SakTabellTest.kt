package no.nav.aap.statistikk.sak

import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.schemaRegistry
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.testutils.BigQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SakTabellTest {
    @Test
    fun `sette inn og hente ut igjen sak`(@BigQuery bigQueryConfig: BigQueryConfig) {
        val client = BigQueryClient(bigQueryConfig, schemaRegistry)

        val sakTabell = SakTabell()

        val referanse = UUID.randomUUID()

        val opprettetTid = LocalDateTime.now()
        client.insert(
            sakTabell, BQSak(
                saksnummer = "123", behandlinger = listOf(
                    Behandling(
                        referanse = referanse,
                        typeBehandling = TypeBehandling.Førstegangsbehandling,
                        opprettetTid = opprettetTid,
                        sak = Sak(
                            saksnummer = "123",
                            person = Person(
                                ident = "213",
                            ),
                        )
                    )
                )
            )
        )

        val uthentet = client.read(sakTabell)

        assertThat(uthentet.size).isEqualTo(1)
        assertThat(uthentet.first().saksnummer).isEqualTo("123")
        assertThat(uthentet.first().behandlinger).containsExactly(
            Behandling(
                referanse = referanse,
                typeBehandling = TypeBehandling.Førstegangsbehandling,
                opprettetTid = opprettetTid,
                sak = Sak(
                    saksnummer = "123",
                    person = Person(
                        ident = "213",
                    ),
                )
            )
        )
    }
}