package no.nav.aap.statistikk.avsluttetbehandling.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.api_kontrakt.Utfall
import no.nav.aap.statistikk.api_kontrakt.Vilkårtype
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.BQRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.schemaRegistry
import no.nav.aap.statistikk.db.FellesKomponentTransactionalExecutor
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.testutils.BigQuery
import no.nav.aap.statistikk.testutils.FakePdlClient
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.opprettTestHendelse
import no.nav.aap.statistikk.tilkjentytelse.BQTilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelseTabell
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.VilkårsVurderingTabell
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
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
            ),
            vilkårsresultat = Vilkårsresultat(
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
            behandlingsReferanse = behandlingReferanse,
            saksnummer = saksnummer,
        )

        val meterRegistry = SimpleMeterRegistry()
        val counter = meterRegistry.avsluttetBehandlingLagret()

        val bigQueryClient = dataSource.transaction {
            val (bigQueryClient, avsluttetBehandlingService) = konstruerAvsluttetBehandlingService(
                dataSource,
                bigQuery,
                counter
            )

            avsluttetBehandlingService.lagre(avsluttetBehandling)

            bigQueryClient
        }


        val utlestVilkårsVurderingFraBigQuery = bigQueryClient.read(VilkårsVurderingTabell())
        val utlestTilkjentYtelseFraBigQuery = bigQueryClient.read(TilkjentYtelseTabell())

        assertThat(utlestVilkårsVurderingFraBigQuery).hasSize(1)
        assertThat(utlestVilkårsVurderingFraBigQuery.first().behandlingsReferanse).isEqualTo(
            avsluttetBehandling.vilkårsresultat.behandlingsReferanse
        )

        assertThat(utlestTilkjentYtelseFraBigQuery).hasSize(2)
        assertThat(utlestTilkjentYtelseFraBigQuery).containsExactlyInAnyOrderElementsOf(
            avsluttetBehandling.tilkjentYtelse.perioder.map {
                BQTilkjentYtelse(
                    saksnummer,
                    behandlingReferanse.toString(),
                    it.fraDato,
                    it.tilDato,
                    it.dagsats,
                    it.gradering
                )
            })

        val uthentetTilkjentYtelse =
            dataSource.transaction { TilkjentYtelseRepository(it).hentTilkjentYtelse(1) }
        assertThat(uthentetTilkjentYtelse).isNotNull()
        assertThat(uthentetTilkjentYtelse!!.perioder).hasSize(2)
        assertThat(uthentetTilkjentYtelse.perioder).isEqualTo(avsluttetBehandling.tilkjentYtelse.perioder)
        assertThat(counter.count()).isEqualTo(1.0)
    }

    @Test
    fun `går fint å hente tilkjent ytelse når ingenting er lagret`(
        @Postgres dataSource: DataSource,
        @BigQuery bigQuery: BigQueryConfig
    ) {
        val behandlingReferanse = UUID.randomUUID()
        val saksnummer = "xxxx"

        val meterRegistry = SimpleMeterRegistry()
        val counter = meterRegistry.avsluttetBehandlingLagret()

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
            ),
            vilkårsresultat = Vilkårsresultat(
                behandlingsReferanse = behandlingReferanse,
                behandlingsType = "Førstegangsbehandling",
                saksnummer = saksnummer,
                vilkår = listOf(
                    Vilkår(
                        vilkårType = Vilkårtype.MEDLEMSKAP,
                        perioder = listOf(
                            VilkårsPeriode(
                                fraDato = LocalDate.now().minusYears(2),
                                tilDato = LocalDate.now(),
                                utfall = Utfall.OPPFYLT.name,
                                manuellVurdering = true
                            )
                        )
                    )
                )
            ),
            beregningsgrunnlag = IBeregningsGrunnlag.GrunnlagYrkesskade(
                grunnlaget = 25000.0,
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
            behandlingsReferanse = behandlingReferanse,
            saksnummer = saksnummer,
        )

        val (_, service) = konstruerAvsluttetBehandlingService(
            dataSource,
            bigQuery,
            counter
        )
        service.lagre(avsluttetBehandling)

        val uthentet =
            dataSource.transaction { TilkjentYtelseRepository(it).hentTilkjentYtelse(1) }!!

        assertThat(uthentet.perioder).isEqualTo(avsluttetBehandling.tilkjentYtelse.perioder)
        assertThat(counter.count()).isEqualTo(1.0)
    }

    private fun konstruerAvsluttetBehandlingService(
        dataSource: DataSource,
        bigQueryConfig: BigQueryConfig,
        counter: Counter
    ): Pair<BigQueryClient, AvsluttetBehandlingService> {
        val bigQueryClient = BigQueryClient(bigQueryConfig, schemaRegistry)
        val bqRepository = BQRepository(bigQueryClient)

        val service =
            AvsluttetBehandlingService(
                FellesKomponentTransactionalExecutor(dataSource),
                { TilkjentYtelseRepository(it) },
                { BeregningsgrunnlagRepository(it) },
                { VilkårsresultatRepository(it) },
                bqRepository,
                behandlingRepositoryFactory = { BehandlingRepository(it) },
                avsluttetBehandlingLagretCounter = counter,
                skjermingService = SkjermingService(FakePdlClient(emptyMap()))
            )
        return Pair(bigQueryClient, service)
    }

}