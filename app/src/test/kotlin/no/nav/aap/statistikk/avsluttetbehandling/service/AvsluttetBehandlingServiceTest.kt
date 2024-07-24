package no.nav.aap.statistikk.avsluttetbehandling.service

import WithBigQueryContainer
import no.nav.aap.statistikk.WithPostgresContainer
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.bigquery.BQRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.TilkjentYtelseTabell
import no.nav.aap.statistikk.bigquery.VilkårsVurderingTabell
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelseService
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import no.nav.aap.statistikk.vilkårsresultat.VilkårsResultatService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

class AvsluttetBehandlingServiceTest : WithPostgresContainer(), WithBigQueryContainer {
    @Test
    fun `avsluttet behandling-objekt lagres både i BigQuery og Postgres`() {
        val bigQueryClient = BigQueryClient(testBigQueryConfig())
        val bqRepository = BQRepository(bigQueryClient)
        val vilkårsResultatService = VilkårsResultatService(postgresDataSource(), bqRepository)
        val tilkjentYtelseRepository = TilkjentYtelseRepository(postgresDataSource())

        val tilkjentYtelseService = TilkjentYtelseService(tilkjentYtelseRepository, bqRepository)

        val service = AvsluttetBehandlingService(vilkårsResultatService, tilkjentYtelseService)

        val behandlingReferanse = UUID.randomUUID()
        val saksnummer = "xxxx"
        val avsluttetBehandling = AvsluttetBehandling(
            tilkjentYtelse = TilkjentYtelse(
                behandlingsReferanse = behandlingReferanse,
                saksnummer = saksnummer,
                perioder = listOf(
                    TilkjentYtelsePeriode(
                        fraDato = LocalDate.now().minusYears(1),
                        tilDato = LocalDate.now().plusDays(1),
                        dagsats = 1337.420,
                        gradering = 90.0
                    )
                )
            ),
            vilkårsresultat = Vilkårsresultat(
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

}