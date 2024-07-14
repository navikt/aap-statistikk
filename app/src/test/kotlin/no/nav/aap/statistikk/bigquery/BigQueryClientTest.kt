package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.api.bigQueryContainer
import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import java.time.LocalDate

class BigQueryClientTest {
    @Test
    fun `lage en tabell to ganger er idempotent`() {
        val options = bigQueryContainer()
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
    fun `sette inn rad`() {
        val options = bigQueryContainer()
        val client = BigQueryClient(options)

        val vilkårsVurderingTabell = VilkårsVurderingTabell()

        client.create(vilkårsVurderingTabell)

        val vilkårsResult = Vilkårsresultat(
            "123", "behandling", listOf(
                Vilkår(
                    "type", listOf(VilkårsPeriode(LocalDate.now(), LocalDate.now(), "utfall", false, null, null))
                )
            )
        )

        client.insert(vilkårsVurderingTabell, vilkårsResult)
        val uthentetResultat = client.read(vilkårsVurderingTabell)

        assertThat(uthentetResultat.size).isEqualTo(1)
        assertThat(uthentetResultat.first().saksnummer).isEqualTo("123")
        assertThat(uthentetResultat.first().behandlingsType).isEqualTo("behandling")
        assertThat(uthentetResultat.first().vilkår).hasSize(1)
        assertThat(uthentetResultat.first().vilkår.first().vilkårType).isEqualTo("type")
    }
}