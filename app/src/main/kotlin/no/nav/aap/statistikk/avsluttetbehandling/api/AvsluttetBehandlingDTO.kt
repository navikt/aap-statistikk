package no.nav.aap.statistikk.avsluttetbehandling.api

import com.papsign.ktor.openapigen.annotations.type.string.example.StringExample
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

data class AvsluttetBehandlingDTO(
    @StringExample("4LELS7K", "4LEFCQ8") val saksnummer: String,
    val behandlingsReferanse: UUID,
    val tilkjentYtelse: TilkjentYtelseDTO,
    val vilkårsResultat: VilkårsResultatDTO,
    val beregningsGrunnlag: BeregningsgrunnlagDTO
) {
    fun tilDomene(): AvsluttetBehandling {
        return AvsluttetBehandling(
            tilkjentYtelse = tilkjentYtelse.tilDomene(
                saksnummer,
                behandlingsReferanse = behandlingsReferanse
            ),
            vilkårsresultat = vilkårsResultat.tilDomene(saksnummer, behandlingsReferanse),
            beregningsgrunnlag = beregningsGrunnlag.tilDomene()
        )
    }
}

data class TilkjentYtelseDTO(
    val perioder: List<TilkjentYtelsePeriodeDTO>
) {
    fun tilDomene(saksnummer: String, behandlingsReferanse: UUID): TilkjentYtelse {
        return TilkjentYtelse(
            saksnummer = saksnummer,
            behandlingsReferanse = behandlingsReferanse,
            perioder = perioder.map(TilkjentYtelsePeriodeDTO::tilDomene)
        )
    }
}

data class TilkjentYtelsePeriodeDTO(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val dagsats: Double,
    val gradering: Double,
) {
    fun tilDomene(): TilkjentYtelsePeriode {
        return TilkjentYtelsePeriode(fraDato = fraDato, tilDato = tilDato, dagsats = dagsats, gradering)
    }
}

data class Grunnlag11_19DTO(
    val inntekter: Map<String, BigDecimal>,
) {
    fun tilDomene(grunnlag: Double, er6GBegrenset: Boolean): IBeregningsGrunnlag.Grunnlag_11_19 {
        return IBeregningsGrunnlag.Grunnlag_11_19(
            grunnlag = grunnlag,
            er6GBegrenset = er6GBegrenset,
            innteker = inntekter
        )
    }
}

data class GrunnlagYrkesskadeDTO(
    val beregningsgrunnlag: Grunnlag11_19DTO,
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
)

/**
 * @property uføreInntekterFraForegåendeÅr Uføre ikke oppjustert
 * @property uføreInntektIKroner Grunnlaget
 */
data class GrunnlagUføreDTO(
    val type: String,
    val grunnlag: Grunnlag11_19DTO,
    val grunnlagYtterligereNedsatt: Grunnlag11_19DTO,
    val uføregrad: Int,
    val uføreInntekterFraForegåendeÅr: Map<String, BigDecimal>,
    val uføreInntektIKroner: BigDecimal,
    val uføreYtterligereNedsattArbeidsevneÅr: Int,
)

/**
 * Felter fra BeregningsGrunnlag-interfacet ([no.nav.aap.behandlingsflyt.faktagrunnlag.delvurdering.beregning.Beregningsgrunnlag]),
 * er alltid med. Minst én av grunnlag11_19dto, grunnlagYrkesskade, grunnlagUføre er ikke-null.
 */
data class BeregningsgrunnlagDTO(
    val grunnlag: Double,
    val er6GBegrenset: Boolean,
    val grunnlag11_19dto: Grunnlag11_19DTO? = null,
    val grunnlagYrkesskade: GrunnlagYrkesskadeDTO? = null,
    val grunnlagUføre: GrunnlagUføreDTO? = null
) {
    init {
        require(grunnlag11_19dto != null || grunnlagYrkesskade != null || grunnlagUføre != null)
    }

    fun tilDomene(): IBeregningsGrunnlag {
        if (grunnlag11_19dto != null) {
            return IBeregningsGrunnlag.Grunnlag_11_19(grunnlag, er6GBegrenset, grunnlag11_19dto.inntekter)
        }
        if (grunnlagYrkesskade != null) {
            return IBeregningsGrunnlag.GrunnlagYrkesskade(
                grunnlag,
                er6GBegrenset,
                grunnlagYrkesskade.beregningsgrunnlag.tilDomene(grunnlag, er6GBegrenset),
                andelYrkesskade = grunnlagYrkesskade.andelYrkesskade,
                andelSomSkyldesYrkesskade = grunnlagYrkesskade.andelSomSkyldesYrkesskade,
                andelSomIkkeSkyldesYrkesskade = grunnlagYrkesskade.andelSomIkkeSkyldesYrkesskade,
                antattÅrligInntektYrkesskadeTidspunktet = grunnlagYrkesskade.antattÅrligInntektYrkesskadeTidspunktet,
                benyttetAndelForYrkesskade = grunnlagYrkesskade.benyttetAndelForYrkesskade,
                grunnlagEtterYrkesskadeFordel = grunnlagYrkesskade.grunnlagEtterYrkesskadeFordel,
                grunnlagForBeregningAvYrkesskadeandel = grunnlagYrkesskade.grunnlagForBeregningAvYrkesskadeandel,
                terskelverdiForYrkesskade = grunnlagYrkesskade.terskelverdiForYrkesskade,
                yrkesskadeinntektIG = grunnlagYrkesskade.yrkesskadeinntektIG,
                yrkesskadeTidspunkt = grunnlagYrkesskade.yrkesskadeTidspunkt
            )
        }
        if (grunnlagUføre != null) {
            return IBeregningsGrunnlag.GrunnlagUføre(
                grunnlag = grunnlag,
                er6GBegrenset = er6GBegrenset,
                type = grunnlagUføre.type,
                grunnlag11_19 = grunnlagUføre.grunnlag.tilDomene(grunnlag = grunnlag, er6GBegrenset = er6GBegrenset),
                uføreInntektIKroner = grunnlagUføre.uføreInntektIKroner,
                uføreInntekterFraForegåendeÅr = grunnlagUføre.uføreInntekterFraForegåendeÅr,
                uføreYtterligereNedsattArbeidsevneÅr = grunnlagUføre.uføreYtterligereNedsattArbeidsevneÅr,
                uføregrad = grunnlagUføre.uføregrad
            )
        }
        throw IllegalStateException()
    }
}