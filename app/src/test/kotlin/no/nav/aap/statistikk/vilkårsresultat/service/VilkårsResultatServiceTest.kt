package no.nav.aap.statistikk.vilkårsresultat.service

import no.nav.aap.statistikk.api.bigQueryContainer
import no.nav.aap.statistikk.api.postgresDataSource
import no.nav.aap.statistikk.bigquery.BQRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.VilkårsVurderingTabell
import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VilkårsResultatServiceTest {
    @Test
    fun `lagrer vilkårsresultat i både pg og bq`() {
        val postgresDataSource = postgresDataSource()
        val bigQueryConfig = bigQueryContainer()
        val bqClient = BigQueryClient(bigQueryConfig)
        val bqRepository = BQRepository(bqClient)

        val vilkårsResultatService = VilkårsResultatService(postgresDataSource, bqRepository)

        val vilkårList = listOf(
            Vilkår(vilkårType = "Vilkår1", listOf()),
        )

        val vilkårsResult = Vilkårsresultat(
            saksnummer = "123456789",
            behandlingsType = "TypeA",
            vilkår = vilkårList
        )

        vilkårsResultatService.mottaVilkårsResultat(vilkårsResult)

        val vilkårsVurderingTabell = VilkårsVurderingTabell()

        val values = bqClient.read(vilkårsVurderingTabell)
        assertThat(values).hasSize(1)
        assertThat(values.first()).isEqualTo(vilkårsResult)
    }
}