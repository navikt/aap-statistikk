package no.nav.aap.statistikk.sak

import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.schemaRegistry
import no.nav.aap.statistikk.testutils.BigQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class SakTabellTest {
    @Test
    fun `sette inn og hente ut igjen sak`(@BigQuery bigQueryConfig: BigQueryConfig) {
        val client = BigQueryClient(bigQueryConfig, schemaRegistry)

        val sakTabell = SakTabell()

        val referanse = UUID.randomUUID()

        val mottattTid =
            LocalDateTime.now().minusDays(1).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        client.insert(
            sakTabell, BQBehandling(
                saksnummer = "123",
                behandlingUUID = referanse.toString(),
                tekniskTid = LocalDateTime.now(),
                behandlingType = TypeBehandling.Revurdering.toString().uppercase(),
                avsender = KELVIN,
                verson = "versjon",
                sekvensNummer = 0L,
                aktorId = "123456",
                mottattTid = mottattTid
            )
        )

        val uthentet = client.read(sakTabell)

        assertThat(uthentet.size).isEqualTo(1)
        assertThat(uthentet.first().saksnummer).isEqualTo("123")
        assertThat(uthentet.first().behandlingUUID).isEqualTo(
            referanse.toString()
        )
        assertThat(uthentet.first().behandlingType).isEqualTo("REVURDERING")
        assertThat(uthentet.first().sekvensNummer).isEqualTo(0L)
        assertThat(uthentet.first().aktorId).isEqualTo("123456")
        assertThat(uthentet.first().mottattTid).isEqualTo(mottattTid)
    }
}