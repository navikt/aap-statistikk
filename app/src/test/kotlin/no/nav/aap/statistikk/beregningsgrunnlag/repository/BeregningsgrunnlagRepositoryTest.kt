package no.nav.aap.statistikk.beregningsgrunnlag.repository

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.UføreType
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.testutils.opprettTestHendelse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import javax.sql.DataSource

class BeregningsgrunnlagRepositoryTest {
    @Test
    fun `sette inn rent 11-19`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        opprettTestHendelse(dataSource, behandlingsReferanse, "ABCDE")

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
            ).hentBeregningsGrunnlag()
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
        opprettTestHendelse(dataSource, behandlingsReferanse, "ABCDE")

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
                ).hentBeregningsGrunnlag()
            }

        assertThat(hentBeregningsGrunnlag).hasSize(1)
        assertThat(hentBeregningsGrunnlag.first()).isEqualTo(
            MedBehandlingsreferanse(
                value = grunnlagYrkesskade,
                behandlingsReferanse = behandlingsReferanse
            )
        )
    }

    @Test
    fun `sette inn grunnlag yrkesskade med uføre`(@Postgres dataSource: DataSource) {
        val behandlingsReferanse = UUID.randomUUID()
        opprettTestHendelse(dataSource, behandlingsReferanse, "ABCDE")

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
                uføregrad = 50,
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
            ).hentBeregningsGrunnlag()
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
        opprettTestHendelse(dataSource, behandlingsReferanse, "ABCDE")

        val grunnlagUfore: IBeregningsGrunnlag.GrunnlagUføre =
            IBeregningsGrunnlag.GrunnlagUføre(
                grunnlag = 30000.0,
                grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
                    grunnlag = 25000.0,
                    er6GBegrenset = false,
                    erGjennomsnitt = true,
                    inntekter = mapOf(2019 to 25000.0, 2020 to 26000.0)
                ),
                uføregrad = 50,
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
            ).hentBeregningsGrunnlag()
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
        opprettTestHendelse(dataSource, behandlingsReferanse, "ABCDE")

        val behandlingsReferanse2 = UUID.randomUUID()

        opprettTestHendelse(dataSource, behandlingsReferanse2, "ABCDF")

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
                uføregrad = 50,
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
                uføregrad = 50,
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
                ).hentBeregningsGrunnlag()
            }

        assertThat(uthentet).hasSize(2)
        assertThat(uthentet.map { it.value }).containsOnly(
            grunnlagUfore,
            grunnlagYrkesskade
        )
    }
}