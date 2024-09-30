package no.nav.aap.statistikk.api_kontrakt

import java.math.BigDecimal

/**
 * Felter fra BeregningsGrunnlag-interfacet ([no.nav.aap.behandlingsflyt.faktagrunnlag.delvurdering.beregning.Beregningsgrunnlag]),
 * er alltid med. Minst én av grunnlag11_19dto, grunnlagYrkesskade, grunnlagUføre er ikke-null.
 */
data class BeregningsgrunnlagDTO(
    val grunnlag11_19dto: Grunnlag11_19DTO? = null,
    val grunnlagYrkesskade: GrunnlagYrkesskadeDTO? = null,
    val grunnlagUføre: GrunnlagUføreDTO? = null
) {
    init {
        require(grunnlag11_19dto != null || grunnlagYrkesskade != null || grunnlagUføre != null)
    }
}

data class Grunnlag11_19DTO(
    val inntekter: Map<String, Double>,
    val grunnlaget: Double,
    val er6GBegrenset: Boolean,
    val erGjennomsnitt: Boolean
)

/**
 * @param [inkludererUføre] Sett til true om [beregningsgrunnlag] er av type [GrunnlagUføreDTO].
 */
data class GrunnlagYrkesskadeDTO(
    val grunnlaget: BigDecimal,
    val inkludererUføre: Boolean,
    val beregningsgrunnlag: BeregningsgrunnlagDTO,
    val terskelverdiForYrkesskade: Int,
    val andelSomSkyldesYrkesskade: BigDecimal,
    val andelYrkesskade: Int,
    val benyttetAndelForYrkesskade: Int,
    val andelSomIkkeSkyldesYrkesskade: BigDecimal,
    val antattÅrligInntektYrkesskadeTidspunktet: BigDecimal,
    val yrkesskadeTidspunkt: Int,
    val grunnlagForBeregningAvYrkesskadeandel: BigDecimal,
    val yrkesskadeinntektIG: BigDecimal,
    val grunnlagEtterYrkesskadeFordel: BigDecimal
)

/**
 * @property uføreInntekterFraForegåendeÅr Uføre ikke oppjustert
 * @property uføreInntektIKroner Grunnlaget
 */
data class GrunnlagUføreDTO(
    val grunnlaget: BigDecimal,
    val type: UføreType,
    val grunnlag: Grunnlag11_19DTO,
    val grunnlagYtterligereNedsatt: Grunnlag11_19DTO,
    val uføregrad: Int,
    val uføreInntekterFraForegåendeÅr: Map<String, Double>,
    val uføreYtterligereNedsattArbeidsevneÅr: Int,
)

enum class UføreType {
    STANDARD, YTTERLIGERE_NEDSATT
}