package no.nav.aap.statistikk.beregningsgrunnlag.repository

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.avsluttetbehandling.GrunnlagType
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
import java.time.Year
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
            yrkesskadeTidspunkt = Year.of(2018),
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
            yrkesskadeTidspunkt = Year.of(2018),
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
            yrkesskadeTidspunkt = Year.of(2018),
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
        val referanser = List(100) { UUID.randomUUID() }
            .associateWith { Pair(genererTilfeldigGrunnlag(), genererTilfeldigGrunnlag()) }

        referanser.forEach { (ref, grunnlag) ->
            opprettTestHendelse(dataSource, ref, Saksnummer("ABCDE-${ref.toString().takeLast(4)}"))

            val (grunnlag1, grunnlag2) = grunnlag
            dataSource.transaction { conn ->
                val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(conn)

                beregningsgrunnlagRepository.lagreBeregningsGrunnlag(
                    MedBehandlingsreferanse(
                        value = grunnlag1,
                        behandlingsReferanse = ref
                    )
                )
                beregningsgrunnlagRepository.lagreBeregningsGrunnlag(
                    MedBehandlingsreferanse(
                        value = grunnlag2,
                        behandlingsReferanse = ref
                    )
                )
            }
        }

        var c = 0
        referanser.forEach { (ref, grunnlag) ->
            val hentet = dataSource.transaction {
                BeregningsgrunnlagRepository(
                    it
                ).hentBeregningsGrunnlag(ref)
            }
            assertThat(hentet).hasSize(1)
            assertThat(hentet.first().behandlingsReferanse).isEqualTo(ref)
            assertThat(hentet.first().value).isEqualTo(grunnlag.second)
            c++
        }
    }

    @Test
    fun `lagre beregningsgrunnlag skal overskrive eksisterende`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        opprettTestHendelse(dataSource, behandlingsReferanse, Saksnummer("ABCDE"))

        val grunnlag1 = IBeregningsGrunnlag.Grunnlag_11_19(
            grunnlag = 20000.0,
            er6GBegrenset = false,
            erGjennomsnitt = true,
            inntekter = mapOf(2019 to 25000.0)
        )

        val grunnlag2 = IBeregningsGrunnlag.Grunnlag_11_19(
            grunnlag = 30000.0,
            er6GBegrenset = true,
            erGjennomsnitt = false,
            inntekter = mapOf(2020 to 35000.0)
        )

        dataSource.transaction {
            val repo = BeregningsgrunnlagRepository(it)
            repo.lagreBeregningsGrunnlag(MedBehandlingsreferanse(behandlingsReferanse, grunnlag1))
        }

        dataSource.transaction {
            val repo = BeregningsgrunnlagRepository(it)
            repo.lagreBeregningsGrunnlag(MedBehandlingsreferanse(behandlingsReferanse, grunnlag2))
        }

        val hentet = dataSource.transaction {
            BeregningsgrunnlagRepository(it).hentBeregningsGrunnlag(behandlingsReferanse)
        }

        assertThat(hentet).hasSize(1)
        assertThat(hentet.first().value).isEqualTo(grunnlag2)
    }

    @Test
    fun `lagre beregningsgrunnlag yrkesskade med ufore skal overskrive eksisterende`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        opprettTestHendelse(dataSource, behandlingsReferanse, Saksnummer("ABCDE"))

        val grunnlagYrkesskadeUfore = IBeregningsGrunnlag.GrunnlagYrkesskade(
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
                ),
                type = UføreType.YTTERLIGERE_NEDSATT,
                uføreInntekterFraForegåendeÅr = mapOf(
                    2019 to 27500.0,
                ),
                uføreYtterligereNedsattArbeidsevneÅr = 2020
            ),
            terskelverdiForYrkesskade = 70,
            andelSomSkyldesYrkesskade = BigDecimal(30),
            andelYrkesskade = 25,
            benyttetAndelForYrkesskade = 20,
            andelSomIkkeSkyldesYrkesskade = BigDecimal(40),
            antattÅrligInntektYrkesskadeTidspunktet = BigDecimal(25000),
            yrkesskadeTidspunkt = Year.of(2018),
            grunnlagForBeregningAvYrkesskadeandel = BigDecimal(25000),
            yrkesskadeinntektIG = BigDecimal(25000),
            grunnlagEtterYrkesskadeFordel = BigDecimal(25000)
        )

        dataSource.transaction {
            val repo = BeregningsgrunnlagRepository(it)
            repo.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    behandlingsReferanse,
                    grunnlagYrkesskadeUfore
                )
            )
        }

        // Lagre igjen for å teste overskriving
        dataSource.transaction {
            val repo = BeregningsgrunnlagRepository(it)
            repo.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    behandlingsReferanse,
                    grunnlagYrkesskadeUfore
                )
            )
        }

        val hentet = dataSource.transaction {
            BeregningsgrunnlagRepository(it).hentBeregningsGrunnlag(behandlingsReferanse)
        }

        assertThat(hentet).hasSize(1)
        assertThat(hentet.first().value).usingRecursiveComparison()
            .isEqualTo(grunnlagYrkesskadeUfore)
    }

    @Test
    fun `lagre beregningsgrunnlag yrkesskade med normal skal overskrive eksisterende`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        opprettTestHendelse(dataSource, behandlingsReferanse, Saksnummer("ABCDE"))

        val grunnlagYrkesskadeNormal = IBeregningsGrunnlag.GrunnlagYrkesskade(
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
            yrkesskadeTidspunkt = Year.of(2018),
            grunnlagForBeregningAvYrkesskadeandel = BigDecimal(25000),
            yrkesskadeinntektIG = BigDecimal(25000),
            grunnlagEtterYrkesskadeFordel = BigDecimal(25000)
        )

        dataSource.transaction {
            val repo = BeregningsgrunnlagRepository(it)
            repo.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    behandlingsReferanse,
                    grunnlagYrkesskadeNormal
                )
            )
        }

        // Lagre igjen for å overskrive
        dataSource.transaction {
            val repo = BeregningsgrunnlagRepository(it)
            repo.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    behandlingsReferanse,
                    grunnlagYrkesskadeNormal
                )
            )
        }

        val hentet = dataSource.transaction {
            BeregningsgrunnlagRepository(it).hentBeregningsGrunnlag(behandlingsReferanse)
        }

        assertThat(hentet).hasSize(1)
        assertThat(hentet.first().value).usingRecursiveComparison()
            .isEqualTo(grunnlagYrkesskadeNormal)
    }

    @Test
    fun `lagre yrkesskade skal ikke etterlate foreldreløse rader ved overskriving`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        opprettTestHendelse(dataSource, behandlingsReferanse, Saksnummer("ABCDE"))

        val grunnlagYrkesskade = IBeregningsGrunnlag.GrunnlagYrkesskade(
            grunnlaget = 25000.0,
            beregningsgrunnlag = IBeregningsGrunnlag.Grunnlag_11_19(
                grunnlag = 20000.0,
                er6GBegrenset = false,
                erGjennomsnitt = true,
                inntekter = mapOf(2019 to 25000.0)
            ),
            terskelverdiForYrkesskade = 70,
            andelSomSkyldesYrkesskade = BigDecimal(30),
            andelYrkesskade = 25,
            benyttetAndelForYrkesskade = 20,
            andelSomIkkeSkyldesYrkesskade = BigDecimal(40),
            antattÅrligInntektYrkesskadeTidspunktet = BigDecimal(25000),
            yrkesskadeTidspunkt = Year.of(2018),
            grunnlagForBeregningAvYrkesskadeandel = BigDecimal(25000),
            yrkesskadeinntektIG = BigDecimal(25000),
            grunnlagEtterYrkesskadeFordel = BigDecimal(25000)
        )

        dataSource.transaction {
            val repo = BeregningsgrunnlagRepository(it)
            repo.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    behandlingsReferanse,
                    grunnlagYrkesskade
                )
            )
        }

        // Lagre igjen for å overskrive
        dataSource.transaction {
            val repo = BeregningsgrunnlagRepository(it)
            repo.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    behandlingsReferanse,
                    grunnlagYrkesskade
                )
            )
        }

        val antallRader = dataSource.transaction {
            it.queryFirst("SELECT count(*) as antall FROM GRUNNLAG_YRKESSKADE") {
                setRowMapper { row -> row.getLong("antall") }
            }
        }

        // Med den gamle koden ville dette vært 2, fordi GRUNNLAG_YRKESSKADE ikke ble slettet
        assertThat(antallRader).isEqualTo(1)
    }

    @Test
    fun `hent yrkesskade med ufore skal fungere selv om id-er er forskjellige`(@Postgres dataSource: DataSource) {
        val ref1 = UUID.randomUUID()
        val ref2 = UUID.randomUUID()
        opprettTestHendelse(dataSource, ref1, Saksnummer("REF01"))
        opprettTestHendelse(dataSource, ref2, Saksnummer("REF02"))

        dataSource.transaction {
            val repo = BeregningsgrunnlagRepository(it)
            // Lagre noe for ref1 først for å dytte GRUNNLAG-sekvensen fremover
            repo.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    ref1, IBeregningsGrunnlag.Grunnlag_11_19(
                        grunnlag = 10000.0,
                        er6GBegrenset = false,
                        erGjennomsnitt = false,
                        inntekter = mapOf(2023 to 10000.0)
                    )
                )
            )
        }

        val grunnlagYrkesskadeUfore = IBeregningsGrunnlag.GrunnlagYrkesskade(
            grunnlaget = 25000.0,
            beregningsgrunnlag = IBeregningsGrunnlag.GrunnlagUføre(
                grunnlag = 30000.0,
                grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
                    grunnlag = 25000.0,
                    er6GBegrenset = false,
                    erGjennomsnitt = true,
                    inntekter = mapOf(2019 to 25000.0)
                ),
                uføregrader = mapOf(LocalDate.of(2020, 1, 1) to 50),
                type = UføreType.YTTERLIGERE_NEDSATT,
                uføreInntekterFraForegåendeÅr = mapOf(2019 to 27500.0),
                uføreYtterligereNedsattArbeidsevneÅr = 2020
            ),
            terskelverdiForYrkesskade = 70,
            andelSomSkyldesYrkesskade = BigDecimal(30),
            andelYrkesskade = 25,
            benyttetAndelForYrkesskade = 20,
            andelSomIkkeSkyldesYrkesskade = BigDecimal(40),
            antattÅrligInntektYrkesskadeTidspunktet = BigDecimal(25000),
            yrkesskadeTidspunkt = Year.of(2018),
            grunnlagForBeregningAvYrkesskadeandel = BigDecimal(25000),
            yrkesskadeinntektIG = BigDecimal(25000),
            grunnlagEtterYrkesskadeFordel = BigDecimal(25000)
        )

        dataSource.transaction {
            BeregningsgrunnlagRepository(it).lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    ref2,
                    grunnlagYrkesskadeUfore
                )
            )
        }

        val hentet = dataSource.transaction {
            BeregningsgrunnlagRepository(it).hentBeregningsGrunnlag(ref2)
        }

        assertThat(hentet).hasSize(1)
        assertThat(hentet.first().value).usingRecursiveComparison()
            .isEqualTo(grunnlagYrkesskadeUfore)
    }
}

fun genererTilfeldigGrunnlag(
    options: List<GrunnlagType> = listOf(
        GrunnlagType.GrunnlagYrkesskade,
        GrunnlagType.Grunnlag_Ufore,
        GrunnlagType.Grunnlag11_19
    )
): IBeregningsGrunnlag {
    options.random().let {
        return when (it) {
            GrunnlagType.Grunnlag11_19 -> genererTilfeldig1119Grunnlag()
            GrunnlagType.Grunnlag_Ufore -> genererTilfeldigUforeGrunnlag()
            GrunnlagType.GrunnlagYrkesskade -> genererTilfeldigYrkesskadeGrunnlag()
        }
    }
}

fun genererTilfeldig1119Grunnlag(): IBeregningsGrunnlag.Grunnlag_11_19 {
    return IBeregningsGrunnlag.Grunnlag_11_19(
        grunnlag = (10000..50000).random().toDouble(),
        er6GBegrenset = randomBool(),
        erGjennomsnitt = randomBool(),
        inntekter = mapOf(
            2019 to (20000..60000).random().toDouble(),
            2020 to (20000..60000).random().toDouble()
        )
    )
}

private fun randomBool(): Boolean = (0..1).random() == 1

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
        beregningsgrunnlag = genererTilfeldigGrunnlag(
            options = listOf(
                GrunnlagType.Grunnlag11_19,
                GrunnlagType.Grunnlag_Ufore
            )
        ),
        terskelverdiForYrkesskade = (50..100).random(),
        andelSomSkyldesYrkesskade = BigDecimal((10..90).random()),
        andelYrkesskade = (10..50).random(),
        benyttetAndelForYrkesskade = (10..50).random(),
        andelSomIkkeSkyldesYrkesskade = BigDecimal((10..90).random()),
        antattÅrligInntektYrkesskadeTidspunktet = BigDecimal((20000..60000).random()),
        yrkesskadeTidspunkt = (2018..2022).random().let(Year::of),
        grunnlagForBeregningAvYrkesskadeandel = BigDecimal((20000..60000).random()),
        yrkesskadeinntektIG = BigDecimal((20000..60000).random()),
        grunnlagEtterYrkesskadeFordel = BigDecimal((20000..60000).random())
    )
}