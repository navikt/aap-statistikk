package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.statistikk.api_kontrakt.UføreType
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import java.math.BigDecimal
import java.util.*

data class AvsluttetBehandling(
    val behandlingsReferanse: UUID,
    val tilkjentYtelse: TilkjentYtelse,
    val vilkårsresultat: Vilkårsresultat,
    val beregningsgrunnlag: IBeregningsGrunnlag?
)

data class MedBehandlingsreferanse<out V>(val behandlingsReferanse: UUID, val value: V)

enum class GrunnlagType {
    Grunnlag11_19 {
        override fun toString() = "11_19"
    },
    Grunnlag_Ufore {
        override fun toString() = "uføre"
    },
    GrunnlagYrkesskade {
        override fun toString() = "yrkesskade"
    }
}

sealed interface IBeregningsGrunnlag {
    /**
     * Hvilket grunnlag som blir brukt som grunnlag for AAP-beregningen.
     */
    fun grunnlaget(): Double


    fun type(): GrunnlagType

    data class Grunnlag_11_19(
        val grunnlag: Double,
        val er6GBegrenset: Boolean,
        val erGjennomsnitt: Boolean,
        val inntekter: Map<Int, Double>
    ) : IBeregningsGrunnlag {
        override fun grunnlaget(): Double {
            return grunnlag
        }

        override fun type(): GrunnlagType {
            return GrunnlagType.Grunnlag11_19
        }
    }

    data class GrunnlagUføre(
        val grunnlag: Double,
        val type: UføreType,
        val grunnlag11_19: Grunnlag_11_19,
        val uføregrad: Int,
        val uføreInntekterFraForegåendeÅr: Map<Int, Double>,
        val uføreInntektIKroner: BigDecimal,
        val uføreYtterligereNedsattArbeidsevneÅr: Int,
    ) : IBeregningsGrunnlag {
        override fun grunnlaget(): Double {
            return grunnlag
        }

        override fun type(): GrunnlagType {
            return GrunnlagType.Grunnlag_Ufore
        }
    }

    data class GrunnlagYrkesskade(
        val grunnlaget: Double,
        val beregningsgrunnlag: IBeregningsGrunnlag,
        // Denne er hardkodet til 70% i behandlingsflyt?
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
    ) : IBeregningsGrunnlag {
        override fun grunnlaget(): Double {
            return grunnlaget
        }

        override fun type(): GrunnlagType {
            return GrunnlagType.GrunnlagYrkesskade
        }
    }
}