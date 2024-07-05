package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.api.bigQueryContainer
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

class BigQueryClientTest {
    @Test
    fun `lage en tabell to ganger er idempotent`() {
        val options = bigQueryContainer()
        val client = BigQueryClient(options)

        val res = client.createIfNotExists("my_table")
        // Lag tabell før den eksisterer
        assertThat(res).isTrue()

        // Prøv igjen
        val res2: Boolean = client.createIfNotExists("my_table")
        assertThat(res2).isFalse()
    }

    @Test
    fun `sette inn rad`() {
        val options = bigQueryContainer()
        val client = BigQueryClient(options)

        client.createIfNotExists("my_table")

        client.insertString("my_table", "dd")

        val res2 = client.read("my_table")

        assertThat(res2!!.size).isEqualTo(1)
        assertThat(res2[0]).isEqualTo("dd")
    }
}