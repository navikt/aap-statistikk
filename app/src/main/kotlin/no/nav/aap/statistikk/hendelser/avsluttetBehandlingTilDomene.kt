package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.*
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.Diagnoser
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypePeriode
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import java.util.*


fun AvsluttetBehandlingDTO.tilDomene(
    saksnummer: Saksnummer,
    behandlingsReferanse: UUID
): AvsluttetBehandling {
    return AvsluttetBehandling(
        tilkjentYtelse = tilkjentYtelse.tilDomene(
            saksnummer,
            behandlingsReferanse = behandlingsReferanse
        ),
        vilkårsresultat = vilkårsResultat.tilDomene(saksnummer, behandlingsReferanse),
        beregningsgrunnlag = if (beregningsGrunnlag == null) null else tilDomene(beregningsGrunnlag!!),
        diagnoser = this.diagnoser?.let { Diagnoser(it.kodeverk, it.diagnosekode, it.bidiagnoser) },
        behandlingsReferanse = behandlingsReferanse,
        behandlingResultat = resultat.resultatTilDomene(),
        rettighetstypeperioder = this.rettighetstypePerioder.map {
            RettighetstypePeriode(
                it.fraDato,
                it.tilDato,
                when (it.rettighetstype) {
                    RettighetsType.BISTANDSBEHOV -> no.nav.aap.statistikk.avsluttetbehandling.RettighetsType.BISTANDSBEHOV
                    RettighetsType.SYKEPENGEERSTATNING -> no.nav.aap.statistikk.avsluttetbehandling.RettighetsType.SYKEPENGEERSTATNING
                    RettighetsType.STUDENT -> no.nav.aap.statistikk.avsluttetbehandling.RettighetsType.STUDENT
                    RettighetsType.ARBEIDSSØKER -> no.nav.aap.statistikk.avsluttetbehandling.RettighetsType.ARBEIDSSØKER
                    RettighetsType.VURDERES_FOR_UFØRETRYGD -> no.nav.aap.statistikk.avsluttetbehandling.RettighetsType.VURDERES_FOR_UFØRETRYGD
                }
            )
        },
    )
}

fun ResultatKode?.resultatTilDomene(): no.nav.aap.statistikk.avsluttetbehandling.ResultatKode? =
    when (this) {
        ResultatKode.INNVILGET -> no.nav.aap.statistikk.avsluttetbehandling.ResultatKode.INNVILGET
        ResultatKode.AVSLAG -> no.nav.aap.statistikk.avsluttetbehandling.ResultatKode.AVSLAG
        ResultatKode.TRUKKET -> no.nav.aap.statistikk.avsluttetbehandling.ResultatKode.TRUKKET
        ResultatKode.KLAGE_TRUKKET -> no.nav.aap.statistikk.avsluttetbehandling.ResultatKode.KLAGE_TRUKKET
        ResultatKode.KLAGE_OPPRETTHOLDES -> no.nav.aap.statistikk.avsluttetbehandling.ResultatKode.KLAGE_OPPRETTHOLDES
        ResultatKode.KLAGE_OMGJØRES -> no.nav.aap.statistikk.avsluttetbehandling.ResultatKode.KLAGE_OMGJØRES
        ResultatKode.KLAGE_DELVIS_OMGJØRES -> no.nav.aap.statistikk.avsluttetbehandling.ResultatKode.KLAGE_DELVIS_OMGJØRES
        ResultatKode.KLAGE_AVSLÅTT -> no.nav.aap.statistikk.avsluttetbehandling.ResultatKode.KLAGE_AVSLÅTT
        null -> null
    }

fun TilkjentYtelseDTO.tilDomene(
    saksnummer: Saksnummer,
    behandlingsReferanse: UUID
): TilkjentYtelse {
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
        redusertDagsats = redusertDagsats,
        gradering = gradering,
        antallBarn = antallBarn,
        barnetilleggSats = barnetilleggSats,
        barnetillegg = barnetillegg
    )
}

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
        type = grunnlagUføreDTO.type.tilDomene(),
        grunnlag11_19 = grunnlagUføreDTO.grunnlag.tilDomene(),
        uføregrad = grunnlagUføreDTO.uføregrad,
        uføreYtterligereNedsattArbeidsevneÅr = grunnlagUføreDTO.uføreYtterligereNedsattArbeidsevneÅr,
        uføreInntekterFraForegåendeÅr = grunnlagUføreDTO.uføreInntekterFraForegåendeÅr.mapKeys { (k, _) -> k.toInt() }
    )
}

fun tilDomene(beregningsgrunnlagDTO: BeregningsgrunnlagDTO): IBeregningsGrunnlag {
    val grunnlagYrkesskade = beregningsgrunnlagDTO.grunnlagYrkesskade
    @Suppress("LocalVariableName") val grunnlag11_19dto = beregningsgrunnlagDTO.grunnlag11_19dto
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
        var beregningsGrunnlag: IBeregningsGrunnlag? = null
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
        )
    }
    if (grunnlagUføre != null) {
        return IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag11_19 = grunnlagUføre.grunnlag.tilDomene(),
            uføreInntekterFraForegåendeÅr = grunnlagUføre.uføreInntekterFraForegåendeÅr.mapKeys { (k, _) -> k.toInt() },
            uføreYtterligereNedsattArbeidsevneÅr = grunnlagUføre.uføreYtterligereNedsattArbeidsevneÅr,
            uføregrad = grunnlagUføre.uføregrad,
            grunnlag = grunnlagUføre.grunnlaget.toDouble(),
            type = grunnlagUføre.type.tilDomene()
        )
    }
    throw IllegalStateException()
}

private fun UføreType.tilDomene(): no.nav.aap.statistikk.avsluttetbehandling.UføreType =
    when (this) {
        UføreType.STANDARD -> no.nav.aap.statistikk.avsluttetbehandling.UføreType.STANDARD
        UføreType.YTTERLIGERE_NEDSATT -> no.nav.aap.statistikk.avsluttetbehandling.UføreType.YTTERLIGERE_NEDSATT
    }