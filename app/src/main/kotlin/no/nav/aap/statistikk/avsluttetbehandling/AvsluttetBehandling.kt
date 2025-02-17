package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class AvsluttetBehandling(
    val behandlingsReferanse: UUID,
    val avsluttetTidspunkt: LocalDateTime,
    val tilkjentYtelse: TilkjentYtelse,
    val vilkårsresultat: Vilkårsresultat,
    val beregningsgrunnlag: IBeregningsGrunnlag?,
    val diagnoser: Diagnoser?,
    val rettighetstypeperioder: List<RettighetstypePeriode> = emptyList()
)

data class RettighetstypePeriode(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val rettighetstype: RettighetsType
)

enum class RettighetsType(val hjemmel: String) {
    BISTANDSBEHOV(hjemmel = "§ 11-6"),
    SYKEPENGEERSTATNING(hjemmel = "§ 11-13"),
    STUDENT(hjemmel = "§ 11-14"),
    ARBEIDSSØKER(hjemmel = "§ 11-17"),
    VURDERES_FOR_UFØRETYGD(hjemmel = "§ 11-18"),
}


data class Diagnoser(
    val kodeverk: String,
    val diagnosekode: String,
    val bidiagnoser: List<String>
)

enum class UføreType {
    STANDARD, YTTERLIGERE_NEDSATT
}

data class MedBehandlingsreferanse<out V>(val behandlingsReferanse: UUID, val value: V)

@Suppress("EnumEntryName")
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
        @Suppress("PropertyName") val grunnlag11_19: Grunnlag_11_19,
        val uføregrad: Int,
        val uføreInntekterFraForegåendeÅr: Map<Int, Double>,
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