package no.nav.aap.statistikk.avsluttetbehandling.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.avsluttetbehandling.*
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.BQYtelseRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.*
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.*
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import no.nav.aap.utbetaling.helved.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.Double
import java.math.BigDecimal
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.function.BiPredicate
import javax.sql.DataSource
import kotlin.Pair
import kotlin.math.abs
import kotlin.to

class AvsluttetBehandlingServiceTest {
    @Test
    fun `avsluttet behandling-objekt lagres både i BigQuery og Postgres`(
        @Postgres dataSource: DataSource,
        @BigQuery bigQuery: BigQueryConfig
    ) {
        val clock = Clock.fixed(Instant.now(), ZoneId.of("Europe/Moscow"))

        val behandlingReferanse = UUID.randomUUID()
        val saksnummer = Saksnummer("xxxx")

        val opprettetTidspunkt = LocalDateTime.now(clock)
        opprettTestHendelse(
            dataSource,
            behandlingReferanse,
            saksnummer,
            status = BehandlingStatus.AVSLUTTET,
            opprettetTidspunkt = opprettetTidspunkt,
            vurderingsbehov = listOf(Vurderingsbehov.OVERGANG_UFORE),
            clock
        )

        val datoNå = LocalDate.now(clock)
        val avsluttetBehandling = AvsluttetBehandling(
            behandlingsReferanse = behandlingReferanse,
            tilkjentYtelse = TilkjentYtelse(
                behandlingsReferanse = behandlingReferanse,
                saksnummer = saksnummer,
                perioder = listOf(
                    TilkjentYtelsePeriode(
                        fraDato = datoNå.minusYears(1),
                        tilDato = datoNå.plusDays(1),
                        dagsats = 1337.420,
                        gradering = 90.0,
                        redusertDagsats = 1337.420 * 0.9,
                        utbetalingsdato = datoNå.minusDays(1),
                        antallBarn = 0,
                        barnetilleggSats = 37.0,
                        barnetillegg = 0.0,
                    ),
                    TilkjentYtelsePeriode(
                        fraDato = datoNå.minusYears(3),
                        tilDato = datoNå.minusYears(2),
                        dagsats = 1234.0,
                        gradering = 45.0,
                        redusertDagsats = 1234.0 * 0.45,
                        utbetalingsdato = datoNå.minusYears(2),
                        antallBarn = 0,
                        barnetilleggSats = 37.0,
                        barnetillegg = 0.0,
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
            behandlingResultat = ResultatKode.INNVILGET,
            rettighetstypeperioder = listOf(
                RettighetstypePeriode(
                    datoNå.minusYears(1),
                    datoNå.minusYears(2),
                    rettighetstype = RettighetsType.BISTANDSBEHOV
                )
            ),
            vedtakstidspunkt = opprettetTidspunkt
        )

        val meterRegistry = SimpleMeterRegistry()
        PrometheusProvider.prometheus = meterRegistry
        val counter = meterRegistry.avsluttetBehandlingLagret()

        val bigQueryClient = dataSource.transaction {
            val (bigQueryClient, avsluttetBehandlingService) = konstruerAvsluttetBehandlingService(
                it,
                bigQuery,
                clock = clock
            )

            avsluttetBehandlingService.lagre(avsluttetBehandling)

            bigQueryClient
        }

        val bqYtelseRepository = BQYtelseRepository(bigQueryClient)
        dataSource.transaction {
            LagreAvsluttetBehandlingTilBigQueryJobbUtfører(
                ytelsesStatistikkTilBigQuery = YtelsesStatistikkTilBigQuery(
                    bqRepository = bqYtelseRepository,
                    behandlingRepository = BehandlingRepository(it),
                    rettighetstypeperiodeRepository = RettighetstypeperiodeRepository(it),
                    diagnoseRepository = DiagnoseRepositoryImpl(it),
                    clock = clock
                )
            ).utfør(JobbInput(mockk()).medPayload(behandlingReferanse))
        }

        val utlestFraBehandlingTabell = bigQueryClient.read(BehandlingTabell()).first()

        val datoSammenligner: BiPredicate<LocalDateTime, LocalDateTime> = BiPredicate { t, u ->
            abs(
                t.toEpochSecond(ZoneOffset.UTC) - u.toEpochSecond(ZoneOffset.UTC)
            ) < 1
        }
        assertThat(utlestFraBehandlingTabell).usingRecursiveComparison()
            .withEqualsForFields(datoSammenligner, "datoAvsluttet", "datoOpprettet")
            .isEqualTo(
                BQYtelseBehandling(
                    saksnummer = saksnummer,
                    referanse = avsluttetBehandling.behandlingsReferanse,
                    brukerFnr = "29021946",
                    behandlingsType = TypeBehandling.Førstegangsbehandling,
                    datoAvsluttet = opprettetTidspunkt,
                    datoOpprettet = opprettetTidspunkt,
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
                    utbetalingId = avsluttetBehandling.behandlingsReferanse.toBase64(),
                    vurderingsbehov = listOf(Vurderingsbehov.OVERGANG_UFORE.name),
                )
            )

        val uthentetTilkjentYtelse =
            dataSource.transaction { TilkjentYtelseRepository(it).hentTilkjentYtelse(1) }
        assertThat(uthentetTilkjentYtelse).isNotNull()
        assertThat(uthentetTilkjentYtelse.perioder).hasSize(2)
        assertThat(uthentetTilkjentYtelse.perioder).usingRecursiveComparison()
            .withComparatorForType(
                { a, b ->
                    val diff = b.toDouble() - a.toDouble()
                    if (abs(diff) < 0.00001) 0 else {
                        if (diff > 0) 1 else -1
                    }
                },
                Double::class.java
            )
            .isEqualTo(avsluttetBehandling.tilkjentYtelse.perioder)
        assertThat(counter.count()).isEqualTo(1.0)
    }

    @Test
    fun `går fint å hente tilkjent ytelse når ingenting er lagret`(
        @Postgres dataSource: DataSource,
        @BigQuery bigQuery: BigQueryConfig
    ) {
        val behandlingReferanse = UUID.randomUUID()
        val saksnummer = Saksnummer("xxxx")

        val meterRegistry = SimpleMeterRegistry()
        PrometheusProvider.prometheus = meterRegistry
        val counter = meterRegistry.avsluttetBehandlingLagret()

        opprettTestHendelse(dataSource, behandlingReferanse, saksnummer)
        opprettTestHendelse(
            dataSource,
            behandlingReferanse,
            saksnummer,
            status = BehandlingStatus.AVSLUTTET
        )

        val nå = LocalDate.now()
        val avsluttetBehandling = AvsluttetBehandling(
            behandlingsReferanse = behandlingReferanse,
            tilkjentYtelse = TilkjentYtelse(
                behandlingsReferanse = behandlingReferanse,
                saksnummer = saksnummer,
                perioder = listOf(
                    TilkjentYtelsePeriode(
                        fraDato = nå.minusYears(3),
                        tilDato = nå.minusYears(2),
                        dagsats = 1234.0,
                        gradering = 45.0,
                        redusertDagsats = 1234.0 * 0.45,
                        utbetalingsdato = nå.minusYears(2),
                        antallBarn = 0,
                        barnetilleggSats = 37.0,
                        barnetillegg = 0.0,
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
            behandlingResultat = ResultatKode.INNVILGET,
            rettighetstypeperioder = listOf(
                RettighetstypePeriode(
                    nå.minusYears(1),
                    nå.minusYears(2),
                    rettighetstype = RettighetsType.BISTANDSBEHOV
                )
            ),
            vedtakstidspunkt = LocalDateTime.now()
        )

        dataSource.transaction {
            val (_, service) = konstruerAvsluttetBehandlingService(
                it,
                bigQuery
            )
            service.lagre(avsluttetBehandling)
        }
        val uthentet = dataSource.transaction {
            dataSource.transaction { TilkjentYtelseRepository(it).hentTilkjentYtelse(1) }
        }

        assertThat(uthentet.perioder).usingRecursiveComparison()
            .withEqualsForType(
                { a, b -> abs(a.toDouble() - b.toDouble()) < 0.00001 },
                Double::class.java
            )
            .ignoringCollectionOrder()
            .isEqualTo(avsluttetBehandling.tilkjentYtelse.perioder)

        assertThat(counter.count()).isEqualTo(1.0)
    }

    private fun konstruerAvsluttetBehandlingService(
        dbConnection: DBConnection,
        bigQueryConfig: BigQueryConfig,
        clock: Clock = Clock.systemUTC()
    ): Pair<BigQueryClient, AvsluttetBehandlingService> {
        val bigQueryClient = BigQueryClient(bigQueryConfig, schemaRegistry)

        val behandlingRepository = BehandlingRepository(dbConnection, clock)
        val service =
            AvsluttetBehandlingService(
                TilkjentYtelseRepository(dbConnection),
                BeregningsgrunnlagRepository(dbConnection),
                VilkårsresultatRepository(dbConnection),
                diagnoseRepository = DiagnoseRepositoryImpl(dbConnection),
                behandlingRepository = behandlingRepository,
                skjermingService = SkjermingService(FakePdlGateway(emptyMap())),
                rettighetstypeperiodeRepository = RettighetstypeperiodeRepository(dbConnection),
                opprettBigQueryLagringYtelseCallback = {}
            )
        return Pair(bigQueryClient, service)
    }

}