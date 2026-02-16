package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.*
import no.nav.aap.komponenter.type.Periode
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.Diagnoser
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypePeriode
import no.nav.aap.statistikk.meldekort.Fritakvurdering
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import java.time.Year
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
        beregningsgrunnlag = beregningsGrunnlag?.let { tilDomene(it) },
        diagnoser = this.diagnoser?.let { Diagnoser(it.kodeverk, it.diagnosekode, it.bidiagnoser) },
        behandlingsReferanse = behandlingsReferanse,
        behandlingResultat = resultat.resultatTilDomene(),
        vedtakstidspunkt = this.vedtakstidspunkt,
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
        fritaksvurderinger = this.fritaksvurderinger?.map {
            Fritakvurdering(
                harFritak = it.harFritak,
                fraDato = it.fraDato,
                tilDato = it.tilDato
            )
        }.orEmpty(),
        perioderMedArbeidsopptrapping = this.perioderMedArbeidsopptrapping.map {
            Periode(it.fom, it.tom)
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
        ResultatKode.AVBRUTT -> no.nav.aap.statistikk.avsluttetbehandling.ResultatKode.AVBRUTT
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
        barnetillegg = barnetillegg,
        utbetalingsdato = this.utbetalingsdato,
        minsteSats = when (this.minsteSats) {
            Minstesats.IKKE_MINSTESATS -> no.nav.aap.statistikk.tilkjentytelse.Minstesats.IKKE_MINSTESATS
            Minstesats.MINSTESATS_OVER_25 -> no.nav.aap.statistikk.tilkjentytelse.Minstesats.MINSTESATS_OVER_25
            Minstesats.MINSTESATS_UNDER_25 -> no.nav.aap.statistikk.tilkjentytelse.Minstesats.MINSTESATS_UNDER_25
        }
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
        yrkesskadeTidspunkt = Year.of(grunnlagYrkesskadeDTO.yrkesskadeTidspunkt),
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
        uføregrader = grunnlagUføreDTO.uføregrader.associate { it.virkningstidspunkt to it.grad },
        uføreYtterligereNedsattArbeidsevneÅr = grunnlagUføreDTO.uføreYtterligereNedsattArbeidsevneÅr,
        uføreInntekterFraForegåendeÅr = grunnlagUføreDTO.uføreInntekterFraForegåendeÅr.mapKeys { (k, _) -> k.toInt() }
    )
}

fun tilDomene(beregningsgrunnlagDTO: BeregningsgrunnlagDTO): IBeregningsGrunnlag {
    val grunnlagYrkesskade = beregningsgrunnlagDTO.grunnlagYrkesskade
    @Suppress("LocalVariableName", "VariableNaming") val grunnlag11_19dto =
        beregningsgrunnlagDTO.grunnlag11_19dto
    val grunnlagUføre = beregningsgrunnlagDTO.grunnlagUføre
    return when {
        grunnlag11_19dto != null -> IBeregningsGrunnlag.Grunnlag_11_19(
            grunnlag11_19dto.grunnlaget,
            grunnlag11_19dto.er6GBegrenset,
            grunnlag11_19dto.erGjennomsnitt,
            grunnlag11_19dto.inntekter.mapKeys { (k, _) -> k.toInt() }
        )

        grunnlagYrkesskade != null -> {
            var beregningsGrunnlag: IBeregningsGrunnlag? = null
            if (grunnlagYrkesskade.beregningsgrunnlag.grunnlagUføre != null) {
                beregningsGrunnlag =
                    grunnlagYrkesskade.beregningsgrunnlag.grunnlagUføre?.let { tilDomene(it) }
            } else if (beregningsgrunnlagDTO.grunnlagYrkesskade?.beregningsgrunnlag?.grunnlagYrkesskade != null) {
                beregningsGrunnlag =
                    grunnlagYrkesskade.beregningsgrunnlag.grunnlagYrkesskade?.let { tilDomene(it) }
            } else if (grunnlagYrkesskade.beregningsgrunnlag.grunnlag11_19dto != null) {
                beregningsGrunnlag =
                    grunnlagYrkesskade.beregningsgrunnlag.grunnlag11_19dto?.tilDomene()
            }
            beregningsGrunnlag =
                requireNotNull(beregningsGrunnlag) { "Beregningsgrunnlag må være satt for yrkesskade" }

            IBeregningsGrunnlag.GrunnlagYrkesskade(
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
                yrkesskadeTidspunkt = Year.of(grunnlagYrkesskade.yrkesskadeTidspunkt),
            )
        }

        grunnlagUføre != null -> IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag11_19 = grunnlagUføre.grunnlag.tilDomene(),
            uføreInntekterFraForegåendeÅr = grunnlagUføre.uføreInntekterFraForegåendeÅr.mapKeys { (k, _) -> k.toInt() },
            uføreYtterligereNedsattArbeidsevneÅr = grunnlagUføre.uføreYtterligereNedsattArbeidsevneÅr,
            uføregrader = grunnlagUføre.uføregrader.associate { it.virkningstidspunkt to it.grad },
            grunnlag = grunnlagUføre.grunnlaget.toDouble(),
            type = grunnlagUføre.type.tilDomene()
        )

        else -> error("Ugyldig tilstand.")
    }
}

private fun UføreType.tilDomene(): no.nav.aap.statistikk.avsluttetbehandling.UføreType =
    when (this) {
        UføreType.STANDARD -> no.nav.aap.statistikk.avsluttetbehandling.UføreType.STANDARD
        UføreType.YTTERLIGERE_NEDSATT -> no.nav.aap.statistikk.avsluttetbehandling.UføreType.YTTERLIGERE_NEDSATT
    }