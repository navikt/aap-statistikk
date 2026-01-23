package no.nav.aap.statistikk.beregningsgrunnlag.repository

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.avsluttetbehandling.UføreType
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.opprettTestHendelse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class BeregningsgrunnlagRepositoryTest {
    @Test
    fun `sette inn rent 11-19`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        opprettTestHendelse(dataSource, behandlingsReferanse, Saksnummer("ABCDE"))

        val grunnlag = IBeregningsGrunnlag.Grunnlag_11_19(
            grunnlag = 20000.0,
            er6GBegrenset = false,
            erGjennomsnitt = true,
            inntekter = mapOf(2019 to 25000.0, 2020 to 26000.0)
        )

        dataSource.transaction {
            val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(it)

            beregningsgrunnlagRepository.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    value = grunnlag,
                    behandlingsReferanse = behandlingsReferanse,
                )
            )
        }

        val hentBeregningsGrunnlag = dataSource.transaction {
            BeregningsgrunnlagRepository(
                it
            ).hentBeregningsGrunnlag(behandlingsReferanse)
        }

        assertThat(hentBeregningsGrunnlag).hasSize(1)
        assertThat(hentBeregningsGrunnlag.first()).isEqualTo(
            MedBehandlingsreferanse(
                value = grunnlag,
                behandlingsReferanse = behandlingsReferanse
            )
        )

    }

    @Test
    fun `sette inn grunnlag yrkesskade`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        opprettTestHendelse(dataSource, behandlingsReferanse, Saksnummer("ABCDE"))

        val grunnlagYrkesskade = IBeregningsGrunnlag.GrunnlagYrkesskade(
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
        )

        dataSource.transaction {
            val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(it)

            beregningsgrunnlagRepository.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    value = grunnlagYrkesskade,
                    behandlingsReferanse = behandlingsReferanse
                )
            )
        }

        val hentBeregningsGrunnlag =
            dataSource.transaction {
                BeregningsgrunnlagRepository(
                    it
                ).hentBeregningsGrunnlag(behandlingsReferanse)
            }

        assertThat(hentBeregningsGrunnlag).hasSize(1).first().isEqualTo(
            MedBehandlingsreferanse(
                value = grunnlagYrkesskade,
                behandlingsReferanse = behandlingsReferanse
            )
        )
    }

    @Test
    fun `sette inn grunnlag yrkesskade med uføre`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        opprettTestHendelse(dataSource, behandlingsReferanse, Saksnummer("ABCDE"))

        val grunnlagYrkesskade = IBeregningsGrunnlag.GrunnlagYrkesskade(
            grunnlaget = 25000.0,
            beregningsgrunnlag = IBeregningsGrunnlag.GrunnlagUføre(
                grunnlag = 30000.0,
                grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
                    grunnlag = 25000.0,
                    er6GBegrenset = false,
                    erGjennomsnitt = true,
                    inntekter = mapOf(2019 to 25000.0, 2020 to 26000.0)
                ),
                uføregrader = mapOf(
                    LocalDate.of(2020, 1, 1) to 50,
                    LocalDate.of(2021, 1, 1) to 60
                ),
                type = UføreType.YTTERLIGERE_NEDSATT,
                uføreInntekterFraForegåendeÅr = mapOf(
                    2018 to 27000.0,
                    2019 to 27500.0,
                    2020 to 28000.0
                ),
                uføreYtterligereNedsattArbeidsevneÅr = 2020
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
        )

        dataSource.transaction {
            val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(it)

            beregningsgrunnlagRepository.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    behandlingsReferanse = behandlingsReferanse,
                    value = grunnlagYrkesskade
                )
            )
        }

        val hentBeregningsGrunnlag = dataSource.transaction {
            BeregningsgrunnlagRepository(
                it
            ).hentBeregningsGrunnlag(behandlingsReferanse)
        }

        assertThat(hentBeregningsGrunnlag).hasSize(1)
        assertThat(hentBeregningsGrunnlag.first()).isEqualTo(
            MedBehandlingsreferanse(
                behandlingsReferanse,
                grunnlagYrkesskade
            )
        )

    }

    @Test
    fun `sette inn grunnlag uføre`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        opprettTestHendelse(dataSource, behandlingsReferanse, Saksnummer("ABCDE"))

        val grunnlagUfore: IBeregningsGrunnlag.GrunnlagUføre =
            IBeregningsGrunnlag.GrunnlagUføre(
                grunnlag = 30000.0,
                grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
                    grunnlag = 25000.0,
                    er6GBegrenset = false,
                    erGjennomsnitt = true,
                    inntekter = mapOf(2019 to 25000.0, 2020 to 26000.0)
                ),
                uføregrader = mapOf(
                    LocalDate.of(2020, 1, 1) to 50,
                    LocalDate.of(2021, 1, 1) to 60
                ),
                type = UføreType.YTTERLIGERE_NEDSATT,
                uføreInntekterFraForegåendeÅr = mapOf(
                    2018 to 27000.0,
                    2019 to 27500.0,
                    2020 to 28000.0,
                ),
                uføreYtterligereNedsattArbeidsevneÅr = 2020
            )

        dataSource.transaction {
            val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(it)

            beregningsgrunnlagRepository.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    value = grunnlagUfore,
                    behandlingsReferanse = behandlingsReferanse
                )
            )
        }

        val uthentet = dataSource.transaction {
            BeregningsgrunnlagRepository(
                it
            ).hentBeregningsGrunnlag(behandlingsReferanse)
        }

        assertThat(uthentet).hasSize(1)
        assertThat(uthentet.first()).isEqualTo(
            MedBehandlingsreferanse(
                value = grunnlagUfore,
                behandlingsReferanse = behandlingsReferanse
            )
        )

    }

    @Test
    fun `sette inn to beregningsgrunnlag`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        opprettTestHendelse(dataSource, behandlingsReferanse, Saksnummer("ABCDE"))

        val behandlingsReferanse2 = UUID.randomUUID()

        opprettTestHendelse(dataSource, behandlingsReferanse2, Saksnummer("ABCDF"))

        val grunnlagYrkesskade = IBeregningsGrunnlag.GrunnlagYrkesskade(
            grunnlaget = 25000.0,
            beregningsgrunnlag = IBeregningsGrunnlag.GrunnlagUføre(
                grunnlag = 30000.0,
                grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
                    grunnlag = 25000.0,
                    er6GBegrenset = false,
                    erGjennomsnitt = true,
                    inntekter = mapOf(2019 to 25000.0, 2020 to 26000.0)
                ),
                uføregrader = mapOf(
                    LocalDate.of(2020, 1, 1) to 50,
                    LocalDate.of(2021, 1, 1) to 60
                ),
                type = UføreType.YTTERLIGERE_NEDSATT,
                uføreInntekterFraForegåendeÅr = mapOf(
                    2018 to 27000.0,
                    2019 to 27500.0,
                    2020 to 28000.0
                ),
                uføreYtterligereNedsattArbeidsevneÅr = 2020
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
        )

        val grunnlagUfore: IBeregningsGrunnlag.GrunnlagUføre =
            IBeregningsGrunnlag.GrunnlagUføre(
                grunnlag = 30000.0,
                grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
                    grunnlag = 25000.0,
                    er6GBegrenset = false,
                    erGjennomsnitt = true,
                    inntekter = mapOf(2019 to 25000.0, 2020 to 26000.0)
                ),
                uføregrader = mapOf(
                    LocalDate.of(2020, 1, 1) to 50,
                    LocalDate.of(2021, 1, 1) to 60
                ),
                type = UføreType.YTTERLIGERE_NEDSATT,
                uføreInntekterFraForegåendeÅr = mapOf(
                    2018 to 27000.0,
                    2019 to 27500.0,
                    2020 to 28000.0
                ),
                uføreYtterligereNedsattArbeidsevneÅr = 2020
            )

        dataSource.transaction {
            val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(it)
            beregningsgrunnlagRepository.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    behandlingsReferanse = behandlingsReferanse,
                    value = grunnlagUfore
                )
            )

            beregningsgrunnlagRepository.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    behandlingsReferanse = behandlingsReferanse2,
                    value = grunnlagYrkesskade
                )
            )
        }

        val uthentet =
            dataSource.transaction {
                BeregningsgrunnlagRepository(
                    it
                ).hentBeregningsGrunnlag(behandlingsReferanse)
            }

        assertThat(uthentet).hasSize(1)
        assertThat(uthentet.map { it.value }).containsOnly(
            grunnlagUfore,
        )

        val uthentet2 = dataSource.transaction {
            BeregningsgrunnlagRepository(
                it
            ).hentBeregningsGrunnlag(behandlingsReferanse2)
        }

        assertThat(uthentet2).hasSize(1)
        assertThat(uthentet2.map { it.value }).usingRecursiveComparison()
            .isEqualTo(listOf(grunnlagYrkesskade))
    }

    @Test
    fun `sette inn mange beregningsgrunnlag`(@Postgres dataSource: DataSource) {
        val referanser = List(50) { UUID.randomUUID() }

        referanser.forEach { ref ->
            opprettTestHendelse(dataSource, ref, Saksnummer("ABCDE-${ref.toString().takeLast(4)}"))

            val grunnlag = genererTilfeldigGrunnlag()


            dataSource.transaction { conn ->
                val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(conn)

                beregningsgrunnlagRepository.lagreBeregningsGrunnlag(
                    MedBehandlingsreferanse(
                        value = grunnlag,
                        behandlingsReferanse = ref
                    )
                )
            }
        }

        referanser.forEach { ref ->
            val hentet = dataSource.transaction {
                BeregningsgrunnlagRepository(
                    it
                ).hentBeregningsGrunnlag(ref)
            }

            assertThat(hentet).hasSize(1)
            assertThat(hentet.first().behandlingsReferanse).isEqualTo(ref)
        }
    }
}

fun genererTilfeldigGrunnlag(
    options: List<String> = listOf(
        "11_19",
        "ufore",
        "yrkesskade"
    )
): IBeregningsGrunnlag {
    options.random().let {
        return when (it) {
            "11_19" -> genererTilfeldig1119Grunnlag()
            "ufore" -> genererTilfeldigUforeGrunnlag()
            "yrkesskade" -> genererTilfeldigYrkesskadeGrunnlag()
            else -> throw IllegalArgumentException("Ukjent grunnlagstype: $it")
        }
    }
}

fun genererTilfeldig1119Grunnlag(): IBeregningsGrunnlag.Grunnlag_11_19 {
    return IBeregningsGrunnlag.Grunnlag_11_19(
        grunnlag = (10000..50000).random().toDouble(),
        er6GBegrenset = (0..1).random() == 1,
        erGjennomsnitt = (0..1).random() == 1,
        inntekter = mapOf(
            2019 to (20000..60000).random().toDouble(),
            2020 to (20000..60000).random().toDouble()
        )
    )
}

fun genererTilfeldigUforeGrunnlag(): IBeregningsGrunnlag.GrunnlagUføre {
    return IBeregningsGrunnlag.GrunnlagUføre(
        grunnlag = (10000..50000).random().toDouble(),
        grunnlag11_19 = genererTilfeldig1119Grunnlag(),
        uføregrader = mapOf(
            LocalDate.of(2015, 1, 1) to (0..100).random(),
            LocalDate.of(2022, 1, 1) to (0..100).random(),
            LocalDate.of(2023, 1, 1) to (0..100).random()
        ),
        type = UføreType.entries.toTypedArray().random(),
        uføreInntekterFraForegåendeÅr = mapOf(
            2018 to (20000..60000).random().toDouble(),
            2019 to (20000..60000).random().toDouble(),
            2020 to (20000..60000).random().toDouble()
        ),
        uføreYtterligereNedsattArbeidsevneÅr = (2018..2022).random()
    )
}

fun genererTilfeldigYrkesskadeGrunnlag(): IBeregningsGrunnlag.GrunnlagYrkesskade {
    return IBeregningsGrunnlag.GrunnlagYrkesskade(
        grunnlaget = (10000..50000).random().toDouble(),
        beregningsgrunnlag = genererTilfeldigGrunnlag(options = listOf("11_19", "ufore")),
        terskelverdiForYrkesskade = (50..100).random(),
        andelSomSkyldesYrkesskade = BigDecimal((10..90).random()),
        andelYrkesskade = (10..50).random(),
        benyttetAndelForYrkesskade = (10..50).random(),
        andelSomIkkeSkyldesYrkesskade = BigDecimal((10..90).random()),
        antattÅrligInntektYrkesskadeTidspunktet = BigDecimal((20000..60000).random()),
        yrkesskadeTidspunkt = (2018..2022).random(),
        grunnlagForBeregningAvYrkesskadeandel = BigDecimal((20000..60000).random()),
        yrkesskadeinntektIG = BigDecimal((20000..60000).random()),
        grunnlagEtterYrkesskadeFordel = BigDecimal((20000..60000).random())
    )
}