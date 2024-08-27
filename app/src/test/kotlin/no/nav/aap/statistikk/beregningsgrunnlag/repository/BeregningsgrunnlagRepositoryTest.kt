package no.nav.aap.statistikk.beregningsgrunnlag.repository

import no.nav.aap.statistikk.Postgres
import no.nav.aap.statistikk.api_kontrakt.UføreType
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import javax.sql.DataSource

class BeregningsgrunnlagRepositoryTest {
    @Test
    fun `sette inn rent 11-19`(@Postgres dataSource: DataSource) {
        val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(dataSource)

        val grunnlag = IBeregningsGrunnlag.Grunnlag_11_19(
            grunnlag = 20000.0,
            er6GBegrenset = false,
            erGjennomsnitt = true,
            inntekter = mapOf(2019 to 25000.0, 2020 to 26000.0)
        )

        beregningsgrunnlagRepository.lagreBeregningsGrunnlag(grunnlag)

        val hentBeregningsGrunnlag = beregningsgrunnlagRepository.hentBeregningsGrunnlag()

        assertThat(hentBeregningsGrunnlag).hasSize(1)
        assertThat(hentBeregningsGrunnlag.first()).isEqualTo(grunnlag)
    }

    @Test
    fun `sette inn grunnlag yrkessakde`(@Postgres dataSource: DataSource) {
        val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(dataSource)

        val grunnlagYrkesskade = IBeregningsGrunnlag.GrunnlagYrkesskade(
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
        )

        beregningsgrunnlagRepository.lagreBeregningsGrunnlag(grunnlagYrkesskade)

        val hentBeregningsGrunnlag = beregningsgrunnlagRepository.hentBeregningsGrunnlag()

        assertThat(hentBeregningsGrunnlag).hasSize(1)
        assertThat(hentBeregningsGrunnlag.first()).isEqualTo(grunnlagYrkesskade)
    }

    @Test
    fun `sette inn grunnlag yrkesskade med uføre`(@Postgres dataSource: DataSource) {
        val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(dataSource)

        val grunnlagYrkesskade = IBeregningsGrunnlag.GrunnlagYrkesskade(
            grunnlaget = 25000.0,
            er6GBegrenset = false,
            beregningsgrunnlag = IBeregningsGrunnlag.GrunnlagUføre(
                grunnlag = 30000.0,
                er6GBegrenset = false,
                grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
                    grunnlag = 25000.0,
                    er6GBegrenset = false,
                    erGjennomsnitt = true,
                    inntekter = mapOf(2019 to 25000.0, 2020 to 26000.0)
                ),
                uføregrad = 50,
                type = UføreType.YTTERLIGERE_NEDSATT,
                uføreInntektIKroner = BigDecimal(28000),
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

        beregningsgrunnlagRepository.lagreBeregningsGrunnlag(grunnlagYrkesskade)

        val hentBeregningsGrunnlag = beregningsgrunnlagRepository.hentBeregningsGrunnlag()

        assertThat(hentBeregningsGrunnlag).hasSize(1)
        assertThat(hentBeregningsGrunnlag.first()).isEqualTo(grunnlagYrkesskade)
    }

    @Test
    fun `sette inn grunnlag uføre`(@Postgres dataSource: DataSource) {
        val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(dataSource)

        val grunnlagUfore: IBeregningsGrunnlag.GrunnlagUføre = IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag = 30000.0,
            er6GBegrenset = false,
            grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
                grunnlag = 25000.0,
                er6GBegrenset = false,
                erGjennomsnitt = true,
                inntekter = mapOf(2019 to 25000.0, 2020 to 26000.0)
            ),
            uføregrad = 50,
            type = UføreType.YTTERLIGERE_NEDSATT,
            uføreInntektIKroner = BigDecimal(28000),
            uføreInntekterFraForegåendeÅr = mapOf(
                2018 to 27000.0,
                2019 to 27500.0,
                2020 to 28000.0,
            ),
            uføreYtterligereNedsattArbeidsevneÅr = 2020
        )

        beregningsgrunnlagRepository.lagreBeregningsGrunnlag(grunnlagUfore)

        val uthentet = beregningsgrunnlagRepository.hentBeregningsGrunnlag()

        assertThat(uthentet).hasSize(1)
        assertThat(uthentet.first()).isEqualTo(grunnlagUfore)
    }

    @Test
    fun `sette inn to beregningsgrunnlag`(@Postgres dataSource: DataSource) {
        val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(dataSource)

        val grunnlagYrkesskade = IBeregningsGrunnlag.GrunnlagYrkesskade(
            grunnlaget = 25000.0,
            er6GBegrenset = false,
            beregningsgrunnlag = IBeregningsGrunnlag.GrunnlagUføre(
                grunnlag = 30000.0,
                er6GBegrenset = false,
                grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
                    grunnlag = 25000.0,
                    er6GBegrenset = false,
                    erGjennomsnitt = true,
                    inntekter = mapOf(2019 to 25000.0, 2020 to 26000.0)
                ),
                uføregrad = 50,
                type = UføreType.YTTERLIGERE_NEDSATT,
                uføreInntektIKroner = BigDecimal(28000),
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

        val grunnlagUfore: IBeregningsGrunnlag.GrunnlagUføre = IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag = 30000.0,
            er6GBegrenset = false,
            grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
                grunnlag = 25000.0,
                er6GBegrenset = false,
                erGjennomsnitt = true,
                inntekter = mapOf(2019 to 25000.0, 2020 to 26000.0)
            ),
            uføregrad = 50,
            type = UføreType.YTTERLIGERE_NEDSATT,
            uføreInntektIKroner = BigDecimal(28000),
            uføreInntekterFraForegåendeÅr = mapOf(
                2018 to 27000.0,
                2019 to 27500.0,
                2020 to 28000.0
            ),
            uføreYtterligereNedsattArbeidsevneÅr = 2020
        )

        beregningsgrunnlagRepository.lagreBeregningsGrunnlag(grunnlagUfore)
        beregningsgrunnlagRepository.lagreBeregningsGrunnlag(grunnlagYrkesskade)

        val uthentet = beregningsgrunnlagRepository.hentBeregningsGrunnlag()

        assertThat(uthentet).hasSize(2)
        assertThat(uthentet).containsOnly(
            grunnlagUfore,
            grunnlagYrkesskade
        )
    }
}