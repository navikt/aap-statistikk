package no.nav.aap.statistikk.vilkårsresultat.service

import WithBigQueryContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import no.nav.aap.statistikk.WithPostgresContainer
import no.nav.aap.statistikk.bigquery.BQRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryObserver
import no.nav.aap.statistikk.bigquery.VilkårsVurderingTabell
import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class VilkårsResultatServiceTest : WithPostgresContainer(), WithBigQueryContainer {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `lagrer vilkårsresultat i både pg og bq`(): Unit = runTest {
        val postgresDataSource = postgresDataSource()
        val bigQueryConfig = testBigQueryConfig()
        val bqClient = BigQueryClient(bigQueryConfig)
        val bqRepository = BQRepository(bqClient)
        val bqObserver = BigQueryObserver(bqRepository, testDispatcher)

        val vilkårsResultatService = VilkårsResultatService(
            postgresDataSource,
        )

        vilkårsResultatService.registerObserver(bqObserver)

        val vilkårList = listOf(
            Vilkår(
                vilkårType = "Vilkår1", listOf(
                    VilkårsPeriode(
                        fraDato = LocalDate.now().minusDays(10),
                        tilDato = LocalDate.now(),
                        utfall = "AVSLAG",
                        manuellVurdering = false
                    )
                )
            ),
        )

        val vilkårsResult = Vilkårsresultat(
            saksnummer = "123456789", behandlingsType = "TypeA", vilkår = vilkårList
        )

        // TODO
        vilkårsResultatService.mottaVilkårsResultat(UUID.randomUUID().toString(), vilkårsResult)

        // Vent på at korutiner fullføres
        testDispatcher.scheduler.advanceUntilIdle()

        // Hent ut data igjen
        val vilkårsVurderingTabell = VilkårsVurderingTabell()
        val values = bqClient.read(vilkårsVurderingTabell)


        // Asserts
        assertThat(values).hasSize(1)
        assertThat(values.first()).isEqualTo(vilkårsResult)
    }
}