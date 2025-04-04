package no.nav.aap.statistikk.avsluttetbehandling.service

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.avsluttetbehandling.*
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.BQYtelseRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.testutils.*
import no.nav.aap.statistikk.tilkjentytelse.BQTilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelseTabell
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.*
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import no.nav.aap.utbetaling.helved.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.*
import java.time.temporal.ChronoUnit
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

        val datoNå = LocalDate.now()
        val avsluttetBehandling = AvsluttetBehandling(
            behandlingsReferanse = behandlingReferanse,
            avsluttetTidspunkt = datoNå.atStartOfDay(),
            tilkjentYtelse = TilkjentYtelse(
                behandlingsReferanse = behandlingReferanse,
                saksnummer = saksnummer,
                perioder = listOf(
                    TilkjentYtelsePeriode(
                        fraDato = datoNå.minusYears(1),
                        tilDato = datoNå.plusDays(1),
                        dagsats = 1337.420,
                        gradering = 90.0
                    ),
                    TilkjentYtelsePeriode(
                        fraDato = datoNå.minusYears(3),
                        tilDato = datoNå.minusYears(2),
                        dagsats = 1234.0,
                        gradering = 45.0
                    )
                )
            ),
            vilkårsresultat = Vilkårsresultat(
                behandlingsReferanse = behandlingReferanse,
                behandlingsType = TypeBehandling.Førstegangsbehandling,
                saksnummer = saksnummer,
                vilkår = listOf(
                    Vilkår(
                        vilkårType = Vilkårtype.ALDERSVILKÅRET, perioder = listOf(
                            VilkårsPeriode(
                                fraDato = datoNå.minusYears(2),
                                tilDato = datoNå.plusDays(3),
                                manuellVurdering = false,
                                utfall = Utfall.OPPFYLT
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
            diagnoser = Diagnoser(
                kodeverk = "KODEVERK",
                diagnosekode = "KOLERA",
                bidiagnoser = listOf("PEST")
            ),
            rettighetstypeperioder = listOf(
                RettighetstypePeriode(
                    datoNå.minusYears(1),
                    datoNå.minusYears(2),
                    rettighetstype = RettighetsType.BISTANDSBEHOV
                )
            )
        )

        val meterRegistry = SimpleMeterRegistry()
        val counter = meterRegistry.avsluttetBehandlingLagret()

        val clock = Clock.fixed(Instant.now(), ZoneId.of("Europe/Oslo"))
        val bigQueryClient = dataSource.transaction {
            val (bigQueryClient, avsluttetBehandlingService) = konstruerAvsluttetBehandlingService(
                it,
                bigQuery,
                meterRegistry,
                clock = clock
            )

            avsluttetBehandlingService.lagre(avsluttetBehandling)

            bigQueryClient
        }


        val utlestFraBehandlingTabell = bigQueryClient.read(BehandlingTabell()).first()
        val utlestVilkårsVurderingFraBigQuery = bigQueryClient.read(VilkårsVurderingTabell())
        val utlestTilkjentYtelseFraBigQuery = bigQueryClient.read(TilkjentYtelseTabell())

        assertThat(utlestFraBehandlingTabell).isEqualTo(
            BQYtelseBehandling(
                saksnummer = saksnummer,
                referanse = avsluttetBehandling.behandlingsReferanse,
                brukerFnr = "29021946",
                behandlingsType = TypeBehandling.Førstegangsbehandling,
                datoAvsluttet = datoNå.atStartOfDay(),
                kodeverk = "KODEVERK",
                diagnosekode = "KOLERA",
                bidiagnoser = listOf("PEST"),
                rettighetsPerioder = listOf(
                    RettighetstypePeriode(
                        datoNå.minusYears(1),
                        datoNå.minusYears(2),
                        rettighetstype = RettighetsType.BISTANDSBEHOV
                    )
                ),
                radEndret = LocalDateTime.now(clock)
                    .truncatedTo(ChronoUnit.MILLIS),
                utbetalingId = avsluttetBehandling.behandlingsReferanse.toBase64()
            )
        )
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
        assertThat(uthentetTilkjentYtelse.perioder).hasSize(2)
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

        val nå = LocalDate.now()
        val avsluttetBehandling = AvsluttetBehandling(
            behandlingsReferanse = behandlingReferanse,
            avsluttetTidspunkt = LocalDateTime.now(),
            tilkjentYtelse = TilkjentYtelse(
                behandlingsReferanse = behandlingReferanse,
                saksnummer = saksnummer,
                perioder = listOf(
                    TilkjentYtelsePeriode(
                        fraDato = nå.minusYears(3),
                        tilDato = nå.minusYears(2),
                        dagsats = 1234.0,
                        gradering = 45.0
                    )
                )
            ),
            vilkårsresultat = Vilkårsresultat(
                behandlingsReferanse = behandlingReferanse,
                behandlingsType = TypeBehandling.Førstegangsbehandling,
                saksnummer = saksnummer,
                vilkår = listOf(
                    Vilkår(
                        vilkårType = Vilkårtype.MEDLEMSKAP,
                        perioder = listOf(
                            VilkårsPeriode(
                                fraDato = nå.minusYears(2),
                                tilDato = nå,
                                utfall = Utfall.OPPFYLT,
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
            diagnoser = Diagnoser(
                kodeverk = "KODEVERK",
                diagnosekode = "KOLERA",
                bidiagnoser = listOf("PEST")
            ),
            rettighetstypeperioder = listOf(
                RettighetstypePeriode(
                    nå.minusYears(1),
                    nå.minusYears(2),
                    rettighetstype = RettighetsType.BISTANDSBEHOV
                )
            )
        )

        dataSource.transaction {
            val (_, service) = konstruerAvsluttetBehandlingService(
                it,
                bigQuery,
                meterRegistry
            )
            service.lagre(avsluttetBehandling)
        }
        val uthentet = dataSource.transaction {
            dataSource.transaction { TilkjentYtelseRepository(it).hentTilkjentYtelse(1) }
        }

        assertThat(uthentet.perioder).isEqualTo(avsluttetBehandling.tilkjentYtelse.perioder)
        assertThat(counter.count()).isEqualTo(1.0)
    }

    private fun konstruerAvsluttetBehandlingService(
        dbConnection: DBConnection,
        bigQueryConfig: BigQueryConfig,
        meterRegistry: MeterRegistry,
        clock: Clock = Clock.systemUTC()
    ): Pair<BigQueryClient, AvsluttetBehandlingService> {
        val bigQueryClient = BigQueryClient(bigQueryConfig, schemaRegistry)
        val bqYtelseRepository = BQYtelseRepository(bigQueryClient)

        val service =
            AvsluttetBehandlingService(
                TilkjentYtelseRepository(dbConnection),
                BeregningsgrunnlagRepository(dbConnection),
                VilkårsresultatRepository(dbConnection),
                diagnoseRepository = DiagnoseRepositoryImpl(dbConnection),
                behandlingRepository = BehandlingRepository(dbConnection),
                skjermingService = SkjermingService(FakePdlClient(emptyMap())),
                rettighetstypeperiodeRepository = RettighetstypeperiodeRepository(dbConnection),
                meterRegistry = meterRegistry,
                ytelsesStatistikkTilBigQuery = YtelsesStatistikkTilBigQuery(
                    bqYtelseRepository,
                    clock
                ),
            )
        return Pair(bigQueryClient, service)
    }

}