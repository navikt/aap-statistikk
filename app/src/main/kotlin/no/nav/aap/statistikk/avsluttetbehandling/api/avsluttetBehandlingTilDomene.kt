package no.nav.aap.statistikk.avsluttetbehandling.api

import no.nav.aap.statistikk.api_kontrakt.*
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import java.util.*

fun AvsluttetBehandlingDTO.tilDomene(): AvsluttetBehandling {
    return AvsluttetBehandling(
        tilkjentYtelse = tilkjentYtelse.tilDomene(
            saksnummer,
            behandlingsReferanse = behandlingsReferanse
        ),
        vilkårsresultat = vilkårsResultat.tilDomene(saksnummer, behandlingsReferanse),
        beregningsgrunnlag = tilDomene(beregningsGrunnlag),
        behandlingsReferanse = behandlingsReferanse,
    )
}

fun TilkjentYtelseDTO.tilDomene(saksnummer: String, behandlingsReferanse: UUID): TilkjentYtelse {
    return TilkjentYtelse(
        saksnummer = saksnummer,
        behandlingsReferanse = behandlingsReferanse,
        perioder = perioder.map(TilkjentYtelsePeriodeDTO::tilDomene)
    )
}

fun TilkjentYtelsePeriodeDTO.tilDomene(): TilkjentYtelsePeriode {
    return TilkjentYtelsePeriode(
        fraDato = fraDato,
        tilDato = tilDato,
        dagsats = dagsats,
        gradering
    )
}

data class MedBehandlingsReferanse<V>(
    val behandlingsReferanse: UUID,
    val value: V,
)

fun Grunnlag11_19DTO.tilDomene(
): IBeregningsGrunnlag.Grunnlag_11_19 {
    return IBeregningsGrunnlag.Grunnlag_11_19(
        grunnlag = grunnlaget,
        er6GBegrenset = er6GBegrenset,
        erGjennomsnitt = erGjennomsnitt,
        inntekter = inntekter.mapKeys { (k, _) -> k.toInt() }
    )
}

fun tilDomene(grunnlagYrkesskadeDTO: GrunnlagYrkesskadeDTO): IBeregningsGrunnlag.GrunnlagYrkesskade {
    return IBeregningsGrunnlag.GrunnlagYrkesskade(
        grunnlaget = grunnlagYrkesskadeDTO.grunnlaget.toDouble(),
        er6GBegrenset =
        grunnlagYrkesskadeDTO.beregningsgrunnlag.grunnlagYrkesskade?.beregningsgrunnlag?.grunnlag11_19dto?.er6GBegrenset
            ?: false,
        beregningsgrunnlag = tilDomene(grunnlagYrkesskadeDTO.beregningsgrunnlag), // Assuming recursive structure handling
        terskelverdiForYrkesskade = grunnlagYrkesskadeDTO.terskelverdiForYrkesskade,
        andelSomSkyldesYrkesskade = grunnlagYrkesskadeDTO.andelSomSkyldesYrkesskade,
        andelYrkesskade = grunnlagYrkesskadeDTO.andelYrkesskade,
        benyttetAndelForYrkesskade = grunnlagYrkesskadeDTO.benyttetAndelForYrkesskade,
        andelSomIkkeSkyldesYrkesskade = grunnlagYrkesskadeDTO.andelSomIkkeSkyldesYrkesskade,
        antattÅrligInntektYrkesskadeTidspunktet = grunnlagYrkesskadeDTO.antattÅrligInntektYrkesskadeTidspunktet,
        yrkesskadeTidspunkt = grunnlagYrkesskadeDTO.yrkesskadeTidspunkt,
        grunnlagForBeregningAvYrkesskadeandel = grunnlagYrkesskadeDTO.grunnlagForBeregningAvYrkesskadeandel,
        yrkesskadeinntektIG = grunnlagYrkesskadeDTO.yrkesskadeinntektIG,
        grunnlagEtterYrkesskadeFordel = grunnlagYrkesskadeDTO.grunnlagEtterYrkesskadeFordel
    )
}

fun tilDomene(grunnlagUføreDTO: GrunnlagUføreDTO): IBeregningsGrunnlag.GrunnlagUføre {
    return IBeregningsGrunnlag.GrunnlagUføre(
        grunnlag = grunnlagUføreDTO.grunnlaget.toDouble(),
        type = grunnlagUføreDTO.type,
        grunnlag11_19 = grunnlagUføreDTO.grunnlag.tilDomene(),
        uføregrad = grunnlagUføreDTO.uføregrad,
        uføreInntektIKroner = grunnlagUføreDTO.uføreInntektIKroner,
        uføreYtterligereNedsattArbeidsevneÅr = grunnlagUføreDTO.uføreYtterligereNedsattArbeidsevneÅr,
        er6GBegrenset = grunnlagUføreDTO.grunnlag.er6GBegrenset, // ?,
        uføreInntekterFraForegåendeÅr = grunnlagUføreDTO.uføreInntekterFraForegåendeÅr.mapKeys { (k, _) -> k.toInt() }
    )
}

fun tilDomene(beregningsgrunnlagDTO: BeregningsgrunnlagDTO): IBeregningsGrunnlag {
    val grunnlagYrkesskade = beregningsgrunnlagDTO.grunnlagYrkesskade
    val grunnlag11_19dto = beregningsgrunnlagDTO.grunnlag11_19dto
    val grunnlagUføre = beregningsgrunnlagDTO.grunnlagUføre
    if (grunnlag11_19dto != null) {
        return IBeregningsGrunnlag.Grunnlag_11_19(
            grunnlag11_19dto.grunnlaget,
            grunnlag11_19dto.er6GBegrenset,
            grunnlag11_19dto.erGjennomsnitt,
            grunnlag11_19dto.inntekter.mapKeys { (k, _) -> k.toInt() }
        )
    }
    if (grunnlagYrkesskade != null) {
        var beregningsGrunnlag: IBeregningsGrunnlag? = null;
        if (grunnlagYrkesskade.beregningsgrunnlag.grunnlagUføre != null) {
            beregningsGrunnlag = tilDomene(grunnlagYrkesskade.beregningsgrunnlag.grunnlagUføre!!)
        } else if (beregningsgrunnlagDTO.grunnlagYrkesskade!!.beregningsgrunnlag.grunnlagYrkesskade != null) {
            beregningsGrunnlag =
                tilDomene(grunnlagYrkesskade.beregningsgrunnlag.grunnlagYrkesskade!!)
        } else if (grunnlagYrkesskade.beregningsgrunnlag.grunnlag11_19dto != null) {
            beregningsGrunnlag =
                grunnlagYrkesskade.beregningsgrunnlag.grunnlag11_19dto?.tilDomene()
        }
        beregningsGrunnlag = requireNotNull(beregningsGrunnlag)

        return IBeregningsGrunnlag.GrunnlagYrkesskade(
            grunnlaget = grunnlagYrkesskade.grunnlaget.toDouble(),
            beregningsgrunnlag = beregningsGrunnlag,
            andelYrkesskade = grunnlagYrkesskade.andelYrkesskade,
            andelSomSkyldesYrkesskade = grunnlagYrkesskade.andelSomSkyldesYrkesskade,
            andelSomIkkeSkyldesYrkesskade = grunnlagYrkesskade.andelSomIkkeSkyldesYrkesskade,
            antattÅrligInntektYrkesskadeTidspunktet = grunnlagYrkesskade.antattÅrligInntektYrkesskadeTidspunktet,
            benyttetAndelForYrkesskade = grunnlagYrkesskade.benyttetAndelForYrkesskade,
            grunnlagEtterYrkesskadeFordel = grunnlagYrkesskade.grunnlagEtterYrkesskadeFordel,
            grunnlagForBeregningAvYrkesskadeandel = grunnlagYrkesskade.grunnlagForBeregningAvYrkesskadeandel,
            terskelverdiForYrkesskade = grunnlagYrkesskade.terskelverdiForYrkesskade,
            yrkesskadeinntektIG = grunnlagYrkesskade.yrkesskadeinntektIG,
            yrkesskadeTidspunkt = grunnlagYrkesskade.yrkesskadeTidspunkt,
            er6GBegrenset = beregningsGrunnlag.er6GBegrenset(),
        )
    }
    if (grunnlagUføre != null) {
        return IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag11_19 = grunnlagUføre.grunnlag.tilDomene(),
            uføreInntektIKroner = grunnlagUføre.uføreInntektIKroner,
            uføreInntekterFraForegåendeÅr = grunnlagUføre.uføreInntekterFraForegåendeÅr.mapKeys { (k, _) -> k.toInt() },
            uføreYtterligereNedsattArbeidsevneÅr = grunnlagUføre.uføreYtterligereNedsattArbeidsevneÅr,
            uføregrad = grunnlagUføre.uføregrad,
            grunnlag = grunnlagUføre.grunnlaget.toDouble(),
            er6GBegrenset = grunnlagUføre.grunnlag.er6GBegrenset,
            type = grunnlagUføre.type
        )
    }
    throw IllegalStateException()
}