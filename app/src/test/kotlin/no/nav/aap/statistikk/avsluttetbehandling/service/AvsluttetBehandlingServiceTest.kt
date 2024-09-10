package no.nav.aap.statistikk.avsluttetbehandling.service

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.BigQuery
import no.nav.aap.statistikk.FellesKomponentTransactionalExecutor
import no.nav.aap.statistikk.Postgres
import no.nav.aap.statistikk.api_kontrakt.Vilkårtype
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.*
import no.nav.aap.statistikk.opprettTestHendelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.VilkårsResultatService
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class AvsluttetBehandlingServiceTest {
    @Test
    fun `avsluttet behandling-objekt lagres både i BigQuery og Postgres`(
        @Postgres dataSource: DataSource,
        @BigQuery bigQuery: BigQueryConfig
    ) {
        val (bigQueryClient, avsluttetBehandlingService) = konstruerTilkjentYtelseService(
            dataSource,
            bigQuery
        )

        val behandlingReferanse = UUID.randomUUID()
        val saksnummer = "xxxx"

        opprettTestHendelse(dataSource, behandlingReferanse, saksnummer)

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
                    ),
                    TilkjentYtelsePeriode(
                        fraDato = LocalDate.now().minusYears(3),
                        tilDato = LocalDate.now().minusYears(2),
                        dagsats = 1234.0,
                        gradering = 45.0
                    )
                )
            ), vilkårsresultat = Vilkårsresultat(
                behandlingsReferanse = behandlingReferanse,
                behandlingsType = "Førstegangsbehandling",
                saksnummer = saksnummer,
                vilkår = listOf(
                    Vilkår(
                        vilkårType = Vilkårtype.ALDERSVILKÅRET, perioder = listOf(
                            VilkårsPeriode(
                                fraDato = LocalDate.now().minusYears(2),
                                tilDato = LocalDate.now().plusDays(3),
                                manuellVurdering = false,
                                utfall = "OPPFYLT"
                            )
                        )
                    )
                )
            ),
            beregningsgrunnlag = IBeregningsGrunnlag.GrunnlagYrkesskade(
                grunnlaget = 25000.0,
                er6GBegrenset = false,
                beregningsgrunnlag = IBeregningsGrunnlag.Grunnlag_11_19(
                    grunnlag = 20000.0,
                    er6GBegrenset = false,
                    erGjennomsnitt = true,
                    inntekter = mapOf(2019 to 25000.0, 2020 to 26000.0)
                ),
                terskelverdiForYrkesskade = 70,
                andelSomSkyldesYrkesskade = BigDecimal(30),
                andelYrkesskade = 25,
                benyttetAndelForYrkesskade = 20,
                andelSomIkkeSkyldesYrkesskade = BigDecimal(40),
                antattÅrligInntektYrkesskadeTidspunktet = BigDecimal(25000),
                yrkesskadeTidspunkt = 2018,
                grunnlagForBeregningAvYrkesskadeandel = BigDecimal(25000),
                yrkesskadeinntektIG = BigDecimal(25000),
                grunnlagEtterYrkesskadeFordel = BigDecimal(25000)
            ),
            behandlingsReferanse = behandlingReferanse
        )

        avsluttetBehandlingService.lagre(avsluttetBehandling)

        val utlestVilkårsVurderingFraBigQuery = bigQueryClient.read(VilkårsVurderingTabell())
        val utlestTilkjentYtelseFraBigQuery = bigQueryClient.read(TilkjentYtelseTabell())

        assertThat(utlestVilkårsVurderingFraBigQuery).hasSize(1)
        assertThat(utlestVilkårsVurderingFraBigQuery.first()).isEqualTo(avsluttetBehandling.vilkårsresultat)

        assertThat(utlestTilkjentYtelseFraBigQuery).hasSize(1)
        assertThat(utlestTilkjentYtelseFraBigQuery.first()).isEqualTo(avsluttetBehandling.tilkjentYtelse)

        val uthentetTilkjentYtelse =
            dataSource.transaction { TilkjentYtelseRepository(it).hentTilkjentYtelse(1) }
        assertThat(uthentetTilkjentYtelse).isNotNull()
        assertThat(uthentetTilkjentYtelse!!.perioder).hasSize(2)
        assertThat(uthentetTilkjentYtelse.perioder).isEqualTo(avsluttetBehandling.tilkjentYtelse.perioder)
    }

    @Test
    fun `går fint å hente tilkjent ytelse når ingenting er lagret`(
        @Postgres dataSource: DataSource,
        @BigQuery bigQuery: BigQueryConfig
    ) {
        val (_, service) = konstruerTilkjentYtelseService(
            dataSource,
            bigQuery
        )

        val behandlingReferanse = UUID.randomUUID()
        val saksnummer = "xxxx"

        opprettTestHendelse(dataSource, behandlingReferanse, saksnummer)

        val avsluttetBehandling = AvsluttetBehandling(
            tilkjentYtelse = TilkjentYtelse(
                behandlingsReferanse = behandlingReferanse,
                saksnummer = saksnummer,
                perioder = listOf(
                    TilkjentYtelsePeriode(
                        fraDato = LocalDate.now().minusYears(3),
                        tilDato = LocalDate.now().minusYears(2),
                        dagsats = 1234.0,
                        gradering = 45.0
                    )
                )
            ), vilkårsresultat = Vilkårsresultat(
                behandlingsReferanse = behandlingReferanse,
                behandlingsType = "Førstegangsbehandling",
                saksnummer = saksnummer,
                vilkår = listOf()
            ),
            beregningsgrunnlag = IBeregningsGrunnlag.GrunnlagYrkesskade(
                grunnlaget = 25000.0,
                er6GBegrenset = false,
                beregningsgrunnlag = IBeregningsGrunnlag.Grunnlag_11_19(
                    grunnlag = 20000.0,
                    er6GBegrenset = false,
                    erGjennomsnitt = true,
                    inntekter = mapOf(2019 to 25000.0, 2020 to 26000.0)
                ),
                terskelverdiForYrkesskade = 70,
                andelSomSkyldesYrkesskade = BigDecimal(30),
                andelYrkesskade = 25,
                benyttetAndelForYrkesskade = 20,
                andelSomIkkeSkyldesYrkesskade = BigDecimal(40),
                antattÅrligInntektYrkesskadeTidspunktet = BigDecimal(25000),
                yrkesskadeTidspunkt = 2018,
                grunnlagForBeregningAvYrkesskadeandel = BigDecimal(25000),
                yrkesskadeinntektIG = BigDecimal(25000),
                grunnlagEtterYrkesskadeFordel = BigDecimal(25000)
            ),
            behandlingsReferanse = behandlingReferanse
        )

        service.lagre(avsluttetBehandling)

        val uthentet =
            dataSource.transaction { TilkjentYtelseRepository(it).hentTilkjentYtelse(1) }!!

        assertThat(uthentet.perioder).isEqualTo(avsluttetBehandling.tilkjentYtelse.perioder)
    }

    private fun konstruerTilkjentYtelseService(
        dataSource: DataSource,
        bigQueryConfig: BigQueryConfig
    ): Pair<BigQueryClient, AvsluttetBehandlingService> {
        val bigQueryClient = BigQueryClient(bigQueryConfig)
        val bqRepository = BQRepository(bigQueryClient)
        val vilkårsResultatService = VilkårsResultatService(dataSource)

        val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(dataSource)

        val service =
            AvsluttetBehandlingService(
                FellesKomponentTransactionalExecutor(dataSource),
                object : Factory<TilkjentYtelseRepository> {
                    override fun create(dbConnection: DBConnection): TilkjentYtelseRepository {
                        return TilkjentYtelseRepository(dbConnection)
                    }
                },
                vilkårsResultatService,
                beregningsgrunnlagRepository,
                bqRepository,
            )
        return Pair(bigQueryClient, service)
    }

}