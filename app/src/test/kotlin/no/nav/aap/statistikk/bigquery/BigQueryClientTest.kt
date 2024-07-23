package no.nav.aap.statistikk.bigquery

import WithBigQueryContainer
import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

class BigQueryClientTest : WithBigQueryContainer {
    @Test
    fun `lage en tabell to ganger er idempotent`() {
        val options = testBigQueryConfig()
        val client = BigQueryClient(options)

        val vilkårsVurderingTabell = VilkårsVurderingTabell()
        val res = client.create(vilkårsVurderingTabell)
        // Lag tabell før den eksisterer
        assertThat(res).isTrue()

        // Prøv igjen
        val res2: Boolean = client.create(vilkårsVurderingTabell)
        assertThat(res2).isFalse()
    }

    @Test
    fun `sette inn rad og hente ut igjen`() {
        val options = testBigQueryConfig()
        val client = BigQueryClient(options)

        val vilkårsVurderingTabell = VilkårsVurderingTabell()

        client.create(vilkårsVurderingTabell)

        val behandlingsReferanse = UUID.randomUUID()
        val vilkårsResult = Vilkårsresultat(
            "123", behandlingsReferanse, "behandling", listOf(
                Vilkår(
                    "type", listOf(VilkårsPeriode(LocalDate.now(), LocalDate.now(), "utfall", false, null, null))
                )
            )
        )

        client.insert(vilkårsVurderingTabell, vilkårsResult)
        val uthentetResultat = client.read(vilkårsVurderingTabell)

        assertThat(uthentetResultat.size).isEqualTo(1)
        assertThat(uthentetResultat.first().saksnummer).isEqualTo("123")
        assertThat(uthentetResultat.first().behandlingsReferanse).isEqualTo(behandlingsReferanse)
        assertThat(uthentetResultat.first().behandlingsType).isEqualTo("behandling")
        assertThat(uthentetResultat.first().vilkår).hasSize(1)
        assertThat(uthentetResultat.first().vilkår.first().vilkårType).isEqualTo("type")
    }
}