package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.type.Periode
import no.nav.aap.statistikk.meldekort.Fritakvurdering
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.util.*

enum class StansType {
    STANS,
    OPPHØR,
}

enum class Avslagsårsak {
    BRUKER_UNDER_18,
    BRUKER_OVER_67,
    MANGLENDE_DOKUMENTASJON,
    IKKE_RETT_PA_SYKEPENGEERSTATNING,
    IKKE_RETT_PA_STUDENT,
    VARIGHET_OVERSKREDET_STUDENT,
    IKKE_SYKDOM_AV_VISS_VARIGHET,
    IKKE_SYKDOM_SKADE_LYTE_VESENTLIGDEL,
    IKKE_SYKDOM_SKADE_LYTE,
    IKKE_NOK_REDUSERT_ARBEIDSEVNE,
    IKKE_BEHOV_FOR_OPPFOLGING,
    IKKE_MEDLEM_FORUTGÅENDE,
    IKKE_MEDLEM,
    IKKE_OPPFYLT_OPPHOLDSKRAV_EØS,
    NORGE_IKKE_KOMPETENT_STAT,
    ANNEN_FULL_YTELSE,
    INNTEKTSTAP_DEKKES_ETTER_ANNEN_LOVGIVNING,
    IKKE_RETT_PA_AAP_UNDER_BEHANDLING_AV_UFORE,
    VARIGHET_OVERSKREDET_OVERGANG_UFORE,
    VARIGHET_OVERSKREDET_ARBEIDSSØKER,
    IKKE_RETT_PA_AAP_I_PERIODE_SOM_ARBEIDSSOKER,
    IKKE_RETT_UNDER_STRAFFEGJENNOMFØRING,
    BRUDD_PÅ_AKTIVITETSPLIKT_STANS,
    BRUDD_PÅ_AKTIVITETSPLIKT_OPPHØR,
    BRUDD_PÅ_OPPHOLDSKRAV_STANS,
    BRUDD_PÅ_OPPHOLDSKRAV_OPPHØR,
    HAR_RETT_TIL_FULLT_UTTAK_ALDERSPENSJON,
    ORDINÆRKVOTE_BRUKT_OPP,
    SYKEPENGEERSTATNINGKVOTE_BRUKT_OPP,
}

data class StansEllerOpphør(
    val type: StansType,
    val fom: LocalDate,
    val årsaker: Set<Avslagsårsak>
)

private val log = LoggerFactory.getLogger("no.nav.aap.statistikk.avsluttetbehandling")

data class AvsluttetBehandling(
    val behandlingsReferanse: UUID,
    val tilkjentYtelse: TilkjentYtelse,
    val vilkårsresultat: Vilkårsresultat,
    val beregningsgrunnlag: IBeregningsGrunnlag?,
    val diagnoser: Diagnoser?,
    val rettighetstypeperioder: List<RettighetstypePeriode> = emptyList(),
    val perioderMedArbeidsopptrapping: List<Periode>,
    val institusjonsopphold: List<Periode> = emptyList(),
    val fritaksvurderinger: List<Fritakvurdering>,
    val behandlingResultat: ResultatKode?,
    val vedtakstidspunkt: LocalDateTime?,
    val vedtattStansOpphør: List<StansEllerOpphør> = emptyList()
) {
    init {
        if (behandlingResultat == null) {
            log.info("Behandlingresultat er null for referanse $behandlingsReferanse")
        }
    }
}

/**
 * Husk å oppdatere tabellen `kodeverk_resultat` også!
 */
enum class ResultatKode {
    INNVILGET,
    AVSLAG,
    TRUKKET,
    KLAGE_OPPRETTHOLDES,
    KLAGE_OMGJØRES,
    KLAGE_DELVIS_OMGJØRES,
    KLAGE_AVSLÅTT,
    KLAGE_TRUKKET,
    AVBRUTT;

    fun sendesTilKA(): Boolean {
        return this == KLAGE_OPPRETTHOLDES || this == KLAGE_DELVIS_OMGJØRES
    }
}

data class RettighetstypePeriode(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val rettighetstype: RettighetsType
)

/**
 * Ved oppdateringer her må tabellen `kodeverk_rettighetstype` oppdateres. Lag migrering!
 */
enum class RettighetsType(@Suppress("unused") val hjemmel: String) {
    BISTANDSBEHOV(hjemmel = "§ 11-6"),
    SYKEPENGEERSTATNING(hjemmel = "§ 11-13"),
    STUDENT(hjemmel = "§ 11-14"),
    ARBEIDSSØKER(hjemmel = "§ 11-17"),
    VURDERES_FOR_UFØRETRYGD(hjemmel = "§ 11-18"),
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

    fun beregningsår(): Year

    fun nedsattArbeidsevneEllerStudieevneDato(): LocalDate

    fun ytterligereNedsattArbeidsevneDato(): LocalDate?

    @Suppress("ClassName")
    data class Grunnlag_11_19(
        val grunnlag: Double,
        val er6GBegrenset: Boolean,
        val erGjennomsnitt: Boolean,
        val inntekter: Map<Int, Double>,
        val nedsattArbeidsevneEllerStudieevneDato: LocalDate,
        val ytterligereNedsattArbeidsevneDato: LocalDate? = null,
    ) : IBeregningsGrunnlag {
        override fun grunnlaget(): Double {
            return grunnlag
        }

        override fun type(): GrunnlagType {
            return GrunnlagType.Grunnlag11_19
        }

        override fun beregningsår(): Year {
            return Year.of(inntekter.keys.max() + 1)
        }

        override fun nedsattArbeidsevneEllerStudieevneDato(): LocalDate =
            nedsattArbeidsevneEllerStudieevneDato

        override fun ytterligereNedsattArbeidsevneDato(): LocalDate? =
            ytterligereNedsattArbeidsevneDato
    }

    data class GrunnlagUføre(
        val grunnlag: Double,
        val type: UføreType,
        @Suppress("PropertyName", "ConstructorParameterNaming") val grunnlag11_19: Grunnlag_11_19,
        val uføregrader: Map<LocalDate, Int>,
        val uføreInntekterFraForegåendeÅr: Map<Int, Double>,
        val uføreYtterligereNedsattArbeidsevneÅr: Int,
        val nedsattArbeidsevneEllerStudieevneDato: LocalDate,
        val ytterligereNedsattArbeidsevneDato: LocalDate? = null,
    ) : IBeregningsGrunnlag {
        override fun grunnlaget(): Double {
            return grunnlag
        }

        override fun type(): GrunnlagType {
            return GrunnlagType.Grunnlag_Ufore
        }

        override fun beregningsår(): Year {
            return when (type) {
                UføreType.STANDARD -> grunnlag11_19.beregningsår()
                UføreType.YTTERLIGERE_NEDSATT -> Year.of(uføreInntekterFraForegåendeÅr.keys.max() + 1)
            }
        }

        override fun nedsattArbeidsevneEllerStudieevneDato(): LocalDate =
            nedsattArbeidsevneEllerStudieevneDato

        override fun ytterligereNedsattArbeidsevneDato(): LocalDate? =
            ytterligereNedsattArbeidsevneDato
    }

    data class GrunnlagYrkesskade(
        val grunnlaget: Double,
        val beregningsgrunnlag: IBeregningsGrunnlag,
        val terskelverdiForYrkesskade: Int,
        val andelSomSkyldesYrkesskade: BigDecimal,
        val andelYrkesskade: Int,
        val benyttetAndelForYrkesskade: Int,
        val andelSomIkkeSkyldesYrkesskade: BigDecimal,
        val antattÅrligInntektYrkesskadeTidspunktet: BigDecimal,
        val yrkesskadeTidspunkt: Year,
        val grunnlagForBeregningAvYrkesskadeandel: BigDecimal,
        val yrkesskadeinntektIG: BigDecimal,
        val grunnlagEtterYrkesskadeFordel: BigDecimal,
        val nedsattArbeidsevneEllerStudieevneDato: LocalDate,
        val ytterligereNedsattArbeidsevneDato: LocalDate? = null,
    ) : IBeregningsGrunnlag {
        override fun grunnlaget(): Double {
            return grunnlaget
        }

        override fun type(): GrunnlagType {
            return GrunnlagType.GrunnlagYrkesskade
        }

        override fun beregningsår(): Year {
            return if (benyttetAndelForYrkesskade == 100) yrkesskadeTidspunkt else
                beregningsgrunnlag.beregningsår()
        }

        override fun nedsattArbeidsevneEllerStudieevneDato(): LocalDate =
            nedsattArbeidsevneEllerStudieevneDato

        override fun ytterligereNedsattArbeidsevneDato(): LocalDate? =
            ytterligereNedsattArbeidsevneDato
    }
}
