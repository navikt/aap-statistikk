package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import java.math.BigDecimal

data class AvsluttetBehandling(
    val tilkjentYtelse: TilkjentYtelse,
    val vilkårsresultat: Vilkårsresultat,
    val beregningsgrunnlag: IBeregningsGrunnlag
)

sealed interface IBeregningsGrunnlag {
    data class Grunnlag_11_19(
        val grunnlag: Double,
        val er6GBegrenset: Boolean,
        val innteker: Map<String, BigDecimal>
    ) : IBeregningsGrunnlag

    data class GrunnlagUføre(
        val grunnlag: Double,
        val er6GBegrenset: Boolean,
        val type: String,
        val grunnlag11_19: Grunnlag_11_19,
        val uføregrad: Int,
        val uføreInntekterFraForegåendeÅr: Map<String, BigDecimal>,
        val uføreInntektIKroner: BigDecimal,
        val uføreYtterligereNedsattArbeidsevneÅr: Int,
    ) : IBeregningsGrunnlag

    data class GrunnlagYrkesskade(
        val grunnlag: Double,
        val er6GBegrenset: Boolean,
        val beregningsgrunnlag: Grunnlag_11_19,
        val terskelverdiForYrkesskade: Int,
        val andelSomSkyldesYrkesskade: BigDecimal,
        val andelYrkesskade: Int,
        val benyttetAndelForYrkesskade: Int,
        val andelSomIkkeSkyldesYrkesskade: BigDecimal,
        val antattÅrligInntektYrkesskadeTidspunktet: BigDecimal,
        val yrkesskadeTidspunkt: Int,
        val grunnlagForBeregningAvYrkesskadeandel: BigDecimal,
        val yrkesskadeinntektIG: BigDecimal,
        val grunnlagEtterYrkesskadeFordel: BigDecimal,
    ) : IBeregningsGrunnlag
}