package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.BeregningsgrunnlagDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Grunnlag11_19DTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.GrunnlagUføreDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.GrunnlagYrkesskadeDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Uføre
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.UføreType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Year
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.UføreType as KontraktUføreType

class BeregningsgrunnlagTilDomeneTest {
    private val ordinært = Grunnlag11_19DTO(
        inntekter = mapOf("2019" to 111_111.25, "2020" to 222_222.5),
        grunnlaget = 123_456.75,
        er6GBegrenset = true,
        erGjennomsnitt = false,
    )
    private val uføre = GrunnlagUføreDTO(
        grunnlaget = "234567.89".toBigDecimal(),
        type = KontraktUføreType.YTTERLIGERE_NEDSATT,
        grunnlag = ordinært,
        grunnlagYtterligereNedsatt = ordinært.copy(grunnlaget = 987_654.32),
        uføregrad = 99,
        uføregrader = listOf(
            Uføre(grad = 30, virkningstidspunkt = LocalDate.of(2020, 3, 1)),
            Uføre(grad = 60, virkningstidspunkt = LocalDate.of(2021, 4, 1)),
        ),
        uføreInntekterFraForegåendeÅr = mapOf("2021" to 100_000.5, "2022" to 200_000.25),
        uføreYtterligereNedsattArbeidsevneÅr = 2023,
    )
    private val ordinærtBeregningsgrunnlag = BeregningsgrunnlagDTO(
        grunnlag11_19dto = ordinært,
        nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2020, 1, 1),
        ytterligereNedsattArbeidsevneDato = null,
    )

    @Test
    fun `konverterer ordinært grunnlag med inntekter og datoer`() {
        val dto = BeregningsgrunnlagDTO(
            grunnlag11_19dto = ordinært,
            nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2023, 5, 1),
            ytterligereNedsattArbeidsevneDato = LocalDate.of(2024, 6, 1),
        )

        assertThat(tilDomene(dto)).isEqualTo(
            IBeregningsGrunnlag.Grunnlag_11_19(
                grunnlag = 123_456.75,
                er6GBegrenset = true,
                erGjennomsnitt = false,
                inntekter = mapOf(2019 to 111_111.25, 2020 to 222_222.5),
                nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2023, 5, 1),
                ytterligereNedsattArbeidsevneDato = LocalDate.of(2024, 6, 1),
            ),
        )
    }

    @Test
    fun `konverterer uføregrunnlag med uføregrader inntekter og datoer`() {
        val dto = BeregningsgrunnlagDTO(
            grunnlagUføre = uføre,
            nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2023, 5, 1),
            ytterligereNedsattArbeidsevneDato = LocalDate.of(2024, 6, 1),
        )

        assertThat(tilDomene(dto)).isEqualTo(
            IBeregningsGrunnlag.GrunnlagUføre(
                grunnlag = 234_567.89,
                type = UføreType.YTTERLIGERE_NEDSATT,
                grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
                    grunnlag = 123_456.75,
                    er6GBegrenset = true,
                    erGjennomsnitt = false,
                    inntekter = mapOf(2019 to 111_111.25, 2020 to 222_222.5),
                    nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2023, 5, 1),
                    ytterligereNedsattArbeidsevneDato = LocalDate.of(2024, 6, 1),
                ),
                uføregrader = mapOf(LocalDate.of(2020, 3, 1) to 30, LocalDate.of(2021, 4, 1) to 60),
                uføreInntekterFraForegåendeÅr = mapOf(2021 to 100_000.5, 2022 to 200_000.25),
                uføreYtterligereNedsattArbeidsevneÅr = 2023,
                nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2023, 5, 1),
                ytterligereNedsattArbeidsevneDato = LocalDate.of(2024, 6, 1),
            ),
        )
    }

    @Test
    fun `konverterer yrkesskade med ordinært grunnlag og ulike datoer`() {
        val dto = BeregningsgrunnlagDTO(
            grunnlagYrkesskade = yrkesskade(ordinærtBeregningsgrunnlag),
            nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2023, 5, 1),
            ytterligereNedsattArbeidsevneDato = LocalDate.of(2024, 6, 1),
        )

        assertThat(tilDomene(dto)).isEqualTo(
            IBeregningsGrunnlag.GrunnlagYrkesskade(
                grunnlaget = 345_678.91,
                beregningsgrunnlag = IBeregningsGrunnlag.Grunnlag_11_19(
                    grunnlag = 123_456.75,
                    er6GBegrenset = true,
                    erGjennomsnitt = false,
                    inntekter = mapOf(2019 to 111_111.25, 2020 to 222_222.5),
                    nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2020, 1, 1),
                    ytterligereNedsattArbeidsevneDato = null,
                ),
                terskelverdiForYrkesskade = 70,
                andelSomSkyldesYrkesskade = "0.3".toBigDecimal(),
                andelYrkesskade = 25,
                benyttetAndelForYrkesskade = 35,
                andelSomIkkeSkyldesYrkesskade = "0.7".toBigDecimal(),
                antattÅrligInntektYrkesskadeTidspunktet = "123456.78".toBigDecimal(),
                yrkesskadeTidspunkt = Year.of(2018),
                grunnlagForBeregningAvYrkesskadeandel = "456789.12".toBigDecimal(),
                yrkesskadeinntektIG = "3.25".toBigDecimal(),
                grunnlagEtterYrkesskadeFordel = "567890.34".toBigDecimal(),
                nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2023, 5, 1),
                ytterligereNedsattArbeidsevneDato = LocalDate.of(2024, 6, 1),
            ),
        )
    }

    @Test
    fun `konverterer yrkesskade med uføregrunnlag og ulike datoer`() {
        val indre = BeregningsgrunnlagDTO(
            grunnlagUføre = uføre.copy(type = KontraktUføreType.STANDARD),
            nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2020, 1, 1),
            ytterligereNedsattArbeidsevneDato = null,
        )
        val dto = BeregningsgrunnlagDTO(
            grunnlagYrkesskade = yrkesskade(indre),
            nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2023, 5, 1),
            ytterligereNedsattArbeidsevneDato = LocalDate.of(2024, 6, 1),
        )

        assertThat(tilDomene(dto)).isEqualTo(
            IBeregningsGrunnlag.GrunnlagYrkesskade(
                grunnlaget = 345_678.91,
                beregningsgrunnlag = IBeregningsGrunnlag.GrunnlagUføre(
                    grunnlag = 234_567.89,
                    type = UføreType.STANDARD,
                    grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
                        grunnlag = 123_456.75,
                        er6GBegrenset = true,
                        erGjennomsnitt = false,
                        inntekter = mapOf(2019 to 111_111.25, 2020 to 222_222.5),
                        nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2020, 1, 1),
                        ytterligereNedsattArbeidsevneDato = null,
                    ),
                    uføregrader = mapOf(LocalDate.of(2020, 3, 1) to 30, LocalDate.of(2021, 4, 1) to 60),
                    uføreInntekterFraForegåendeÅr = mapOf(2021 to 100_000.5, 2022 to 200_000.25),
                    uføreYtterligereNedsattArbeidsevneÅr = 2023,
                    nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2020, 1, 1),
                    ytterligereNedsattArbeidsevneDato = null,
                ),
                terskelverdiForYrkesskade = 70,
                andelSomSkyldesYrkesskade = "0.3".toBigDecimal(),
                andelYrkesskade = 25,
                benyttetAndelForYrkesskade = 35,
                andelSomIkkeSkyldesYrkesskade = "0.7".toBigDecimal(),
                antattÅrligInntektYrkesskadeTidspunktet = "123456.78".toBigDecimal(),
                yrkesskadeTidspunkt = Year.of(2018),
                grunnlagForBeregningAvYrkesskadeandel = "456789.12".toBigDecimal(),
                yrkesskadeinntektIG = "3.25".toBigDecimal(),
                grunnlagEtterYrkesskadeFordel = "567890.34".toBigDecimal(),
                nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2023, 5, 1),
                ytterligereNedsattArbeidsevneDato = LocalDate.of(2024, 6, 1),
            ),
        )
    }

    @Test
    fun `konverterer nestede yrkesskadegrunnlag med ulike datoer på hvert nivå`() {
        val indre = BeregningsgrunnlagDTO(
            grunnlagYrkesskade = yrkesskade(ordinærtBeregningsgrunnlag),
            nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2021, 2, 1),
            ytterligereNedsattArbeidsevneDato = LocalDate.of(2022, 3, 1),
        )
        val dto = BeregningsgrunnlagDTO(
            grunnlagYrkesskade = yrkesskade(indre),
            nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2023, 5, 1),
            ytterligereNedsattArbeidsevneDato = LocalDate.of(2024, 6, 1),
        )

        assertThat(tilDomene(dto)).isEqualTo(
            IBeregningsGrunnlag.GrunnlagYrkesskade(
                grunnlaget = 345_678.91,
                beregningsgrunnlag = IBeregningsGrunnlag.GrunnlagYrkesskade(
                    grunnlaget = 345_678.91,
                    beregningsgrunnlag = IBeregningsGrunnlag.Grunnlag_11_19(
                        grunnlag = 123_456.75,
                        er6GBegrenset = true,
                        erGjennomsnitt = false,
                        inntekter = mapOf(2019 to 111_111.25, 2020 to 222_222.5),
                        nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2020, 1, 1),
                        ytterligereNedsattArbeidsevneDato = null,
                    ),
                    terskelverdiForYrkesskade = 70,
                    andelSomSkyldesYrkesskade = "0.3".toBigDecimal(),
                    andelYrkesskade = 25,
                    benyttetAndelForYrkesskade = 35,
                    andelSomIkkeSkyldesYrkesskade = "0.7".toBigDecimal(),
                    antattÅrligInntektYrkesskadeTidspunktet = "123456.78".toBigDecimal(),
                    yrkesskadeTidspunkt = Year.of(2018),
                    grunnlagForBeregningAvYrkesskadeandel = "456789.12".toBigDecimal(),
                    yrkesskadeinntektIG = "3.25".toBigDecimal(),
                    grunnlagEtterYrkesskadeFordel = "567890.34".toBigDecimal(),
                    nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2021, 2, 1),
                    ytterligereNedsattArbeidsevneDato = LocalDate.of(2022, 3, 1),
                ),
                terskelverdiForYrkesskade = 70,
                andelSomSkyldesYrkesskade = "0.3".toBigDecimal(),
                andelYrkesskade = 25,
                benyttetAndelForYrkesskade = 35,
                andelSomIkkeSkyldesYrkesskade = "0.7".toBigDecimal(),
                antattÅrligInntektYrkesskadeTidspunktet = "123456.78".toBigDecimal(),
                yrkesskadeTidspunkt = Year.of(2018),
                grunnlagForBeregningAvYrkesskadeandel = "456789.12".toBigDecimal(),
                yrkesskadeinntektIG = "3.25".toBigDecimal(),
                grunnlagEtterYrkesskadeFordel = "567890.34".toBigDecimal(),
                nedsattArbeidsevneEllerStudieevneDato = LocalDate.of(2023, 5, 1),
                ytterligereNedsattArbeidsevneDato = LocalDate.of(2024, 6, 1),
            ),
        )
    }

    private fun yrkesskade(grunnlag: BeregningsgrunnlagDTO) = GrunnlagYrkesskadeDTO(
        grunnlaget = "345678.91".toBigDecimal(),
        inkludererUføre = grunnlag.grunnlagUføre != null,
        beregningsgrunnlag = grunnlag,
        terskelverdiForYrkesskade = 70,
        andelSomSkyldesYrkesskade = "0.3".toBigDecimal(),
        andelYrkesskade = 25,
        benyttetAndelForYrkesskade = 35,
        andelSomIkkeSkyldesYrkesskade = "0.7".toBigDecimal(),
        antattÅrligInntektYrkesskadeTidspunktet = "123456.78".toBigDecimal(),
        yrkesskadeTidspunkt = 2018,
        grunnlagForBeregningAvYrkesskadeandel = "456789.12".toBigDecimal(),
        yrkesskadeinntektIG = "3.25".toBigDecimal(),
        grunnlagEtterYrkesskadeFordel = "567890.34".toBigDecimal(),
    )
}
