package no.nav.aap.statistikk.avsluttetbehandling.service

import BigQuery
import no.nav.aap.statistikk.Postgres
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.bigquery.*
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelseService
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.VilkårsResultatService
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class AvsluttetBehandlingServiceTest {
    @Test
    fun `avsluttet behandling-objekt lagres både i BigQuery og Postgres`(
        @Postgres dataSource: DataSource,
        @BigQuery bigQuery: BigQueryConfig
    ) {
        val (tilkjentYtelseRepository, bigQueryClient, service) = konstruerTilkjentYtelseService(dataSource, bigQuery)

        val behandlingReferanse = UUID.randomUUID()
        val saksnummer = "xxxx"
        val avsluttetBehandling = AvsluttetBehandling(
            tilkjentYtelse = TilkjentYtelse(
                behandlingsReferanse = behandlingReferanse, saksnummer = saksnummer, perioder = listOf(
                    TilkjentYtelsePeriode(
                        fraDato = LocalDate.now().minusYears(1),
                        tilDato = LocalDate.now().plusDays(1),
                        dagsats = 1337.420,
                        gradering = 90.0
                    )
                )
            ), vilkårsresultat = Vilkårsresultat(
                behandlingsReferanse = behandlingReferanse,
                behandlingsType = "Førstegangsbehandling",
                saksnummer = saksnummer,
                vilkår = listOf(
                    Vilkår(
                        vilkårType = "ALDERSVILKÅR", perioder = listOf(
                            VilkårsPeriode(
                                fraDato = LocalDate.now().minusYears(2),
                                tilDato = LocalDate.now().plusDays(3),
                                manuellVurdering = false,
                                utfall = "OPPFYLT"
                            )
                        )
                    )
                )
            )
        )

        service.lagre(avsluttetBehandling)

        val utlestVilkårsVurderingFraBigQuery = bigQueryClient.read(VilkårsVurderingTabell())
        val utlestTilkjentYtelseFraBigQuery = bigQueryClient.read(TilkjentYtelseTabell())

        assertThat(utlestVilkårsVurderingFraBigQuery).hasSize(1)
        assertThat(utlestVilkårsVurderingFraBigQuery.first()).isEqualTo(avsluttetBehandling.vilkårsresultat)

        assertThat(utlestTilkjentYtelseFraBigQuery).hasSize(1)
        assertThat(utlestTilkjentYtelseFraBigQuery.first()).isEqualTo(avsluttetBehandling.tilkjentYtelse)

        val uthentetTilkjentYtelse = tilkjentYtelseRepository.hentTilkjentYtelse(1)
        assertThat(uthentetTilkjentYtelse).isNotNull()
        assertThat(uthentetTilkjentYtelse!!.perioder).hasSize(1)
        assertThat(uthentetTilkjentYtelse.perioder).isEqualTo(avsluttetBehandling.tilkjentYtelse.perioder)
    }

    @Test
    fun `går fint å hente tilkjent ytelse når ingenting er lagret`(
        @Postgres dataSource: DataSource,
        @BigQuery bigQuery: BigQueryConfig
    ) {
        val (tilkjentYtelseRepository, _, service) = konstruerTilkjentYtelseService(dataSource, bigQuery)

        val behandlingReferanse = UUID.randomUUID()
        val saksnummer = "xxxx"
        val avsluttetBehandling = AvsluttetBehandling(
            tilkjentYtelse = TilkjentYtelse(
                behandlingsReferanse = behandlingReferanse, saksnummer = saksnummer, perioder = listOf()
            ), vilkårsresultat = Vilkårsresultat(
                behandlingsReferanse = behandlingReferanse,
                behandlingsType = "Førstegangsbehandling",
                saksnummer = saksnummer,
                vilkår = listOf()
            )
        )

        service.lagre(avsluttetBehandling)

        assertThat(tilkjentYtelseRepository.hentTilkjentYtelse(1)).isNull()

    }

    private fun konstruerTilkjentYtelseService(
        dataSource: DataSource,
        bigQueryConfig: BigQueryConfig
    ): Triple<TilkjentYtelseRepository, BigQueryClient, AvsluttetBehandlingService> {
        val bigQueryClient = BigQueryClient(bigQueryConfig)
        val bqRepository = BQRepository(bigQueryClient)
        val vilkårsResultatService = VilkårsResultatService(dataSource, bqRepository)
        val tilkjentYtelseRepository = TilkjentYtelseRepository(dataSource)

        val tilkjentYtelseService = TilkjentYtelseService(tilkjentYtelseRepository, bqRepository)

        val service = AvsluttetBehandlingService(vilkårsResultatService, tilkjentYtelseService)
        return Triple(tilkjentYtelseRepository, bigQueryClient, service)
    }

}