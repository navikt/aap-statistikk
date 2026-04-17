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
import java.time.LocalDate
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
        vedtattStansOpphør = this.vedtattStansOpphør.map { it.tilDomene() },
    )
}

private fun StansEllerOpphør.tilDomene() =
    no.nav.aap.statistikk.avsluttetbehandling.StansEllerOpphør(
        type = when (type) {
            Avslagstype.STANS -> no.nav.aap.statistikk.avsluttetbehandling.StansType.STANS
            Avslagstype.OPPHØR -> no.nav.aap.statistikk.avsluttetbehandling.StansType.OPPHØR
        },
        fom = fom,
        årsaker = årsaker.map { it.tilDomene() }.toSet()
    )

private fun Avslagsårsak.tilDomene(): no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak =
    when (this) {
        Avslagsårsak.BRUKER_UNDER_18 -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.BRUKER_UNDER_18
        Avslagsårsak.BRUKER_OVER_67 -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.BRUKER_OVER_67
        Avslagsårsak.MANGLENDE_DOKUMENTASJON -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.MANGLENDE_DOKUMENTASJON
        Avslagsårsak.IKKE_RETT_PA_SYKEPENGEERSTATNING -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.IKKE_RETT_PA_SYKEPENGEERSTATNING
        Avslagsårsak.IKKE_RETT_PA_STUDENT -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.IKKE_RETT_PA_STUDENT
        Avslagsårsak.VARIGHET_OVERSKREDET_STUDENT -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.VARIGHET_OVERSKREDET_STUDENT
        Avslagsårsak.IKKE_SYKDOM_AV_VISS_VARIGHET -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.IKKE_SYKDOM_AV_VISS_VARIGHET
        Avslagsårsak.IKKE_SYKDOM_SKADE_LYTE_VESENTLIGDEL -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.IKKE_SYKDOM_SKADE_LYTE_VESENTLIGDEL
        Avslagsårsak.IKKE_NOK_REDUSERT_ARBEIDSEVNE -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.IKKE_NOK_REDUSERT_ARBEIDSEVNE
        Avslagsårsak.IKKE_BEHOV_FOR_OPPFOLGING -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.IKKE_BEHOV_FOR_OPPFOLGING
        Avslagsårsak.IKKE_MEDLEM_FORUTGÅENDE -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.IKKE_MEDLEM_FORUTGÅENDE
        Avslagsårsak.IKKE_MEDLEM -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.IKKE_MEDLEM
        Avslagsårsak.IKKE_OPPFYLT_OPPHOLDSKRAV_EØS -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.IKKE_OPPFYLT_OPPHOLDSKRAV_EØS
        Avslagsårsak.NORGE_IKKE_KOMPETENT_STAT -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.NORGE_IKKE_KOMPETENT_STAT
        Avslagsårsak.ANNEN_FULL_YTELSE -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.ANNEN_FULL_YTELSE
        Avslagsårsak.INNTEKTSTAP_DEKKES_ETTER_ANNEN_LOVGIVNING -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.INNTEKTSTAP_DEKKES_ETTER_ANNEN_LOVGIVNING
        Avslagsårsak.IKKE_RETT_PA_AAP_UNDER_BEHANDLING_AV_UFORE -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.IKKE_RETT_PA_AAP_UNDER_BEHANDLING_AV_UFORE
        Avslagsårsak.VARIGHET_OVERSKREDET_OVERGANG_UFORE -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.VARIGHET_OVERSKREDET_OVERGANG_UFORE
        Avslagsårsak.VARIGHET_OVERSKREDET_ARBEIDSSØKER -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.VARIGHET_OVERSKREDET_ARBEIDSSØKER
        Avslagsårsak.IKKE_RETT_PA_AAP_I_PERIODE_SOM_ARBEIDSSOKER -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.IKKE_RETT_PA_AAP_I_PERIODE_SOM_ARBEIDSSOKER
        Avslagsårsak.IKKE_RETT_UNDER_STRAFFEGJENNOMFØRING -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.IKKE_RETT_UNDER_STRAFFEGJENNOMFØRING
        Avslagsårsak.BRUDD_PÅ_AKTIVITETSPLIKT_STANS -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.BRUDD_PÅ_AKTIVITETSPLIKT_STANS
        Avslagsårsak.BRUDD_PÅ_AKTIVITETSPLIKT_OPPHØR -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.BRUDD_PÅ_AKTIVITETSPLIKT_OPPHØR
        Avslagsårsak.BRUDD_PÅ_OPPHOLDSKRAV_STANS -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.BRUDD_PÅ_OPPHOLDSKRAV_STANS
        Avslagsårsak.BRUDD_PÅ_OPPHOLDSKRAV_OPPHØR -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.BRUDD_PÅ_OPPHOLDSKRAV_OPPHØR
        Avslagsårsak.HAR_RETT_TIL_FULLT_UTTAK_ALDERSPENSJON -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.HAR_RETT_TIL_FULLT_UTTAK_ALDERSPENSJON
        Avslagsårsak.ORDINÆRKVOTE_BRUKT_OPP -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.ORDINÆRKVOTE_BRUKT_OPP
        Avslagsårsak.SYKEPENGEERSTATNINGKVOTE_BRUKT_OPP -> no.nav.aap.statistikk.avsluttetbehandling.Avslagsårsak.SYKEPENGEERSTATNINGKVOTE_BRUKT_OPP
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
        barnepensjonDagsats = barnepensjonDagsats,
        utbetalingsdato = this.utbetalingsdato,
        minsteSats = when (this.minsteSats) {
            Minstesats.IKKE_MINSTESATS -> no.nav.aap.statistikk.tilkjentytelse.Minstesats.IKKE_MINSTESATS
            Minstesats.MINSTESATS_OVER_25 -> no.nav.aap.statistikk.tilkjentytelse.Minstesats.MINSTESATS_OVER_25
            Minstesats.MINSTESATS_UNDER_25 -> no.nav.aap.statistikk.tilkjentytelse.Minstesats.MINSTESATS_UNDER_25
        },
        samordningGradering = samordningGradering,
        institusjonGradering = institusjonGradering,
        arbeidGradering = arbeidGradering,
        samordningUføregradering = samordningUføregradering,
        samordningArbeidsgiverGradering = samordningArbeidsgiverGradering,
        meldepliktGradering = meldepliktGradering,
    )
}

fun Grunnlag11_19DTO.tilDomene(
    nedsattArbeidsevneEllerStudieevneDato: LocalDate,
    ytterligereNedsattArbeidsevneDato: LocalDate? = null,
): IBeregningsGrunnlag.Grunnlag_11_19 {
    return IBeregningsGrunnlag.Grunnlag_11_19(
        grunnlag = grunnlaget,
        er6GBegrenset = er6GBegrenset,
        erGjennomsnitt = erGjennomsnitt,
        inntekter = inntekter.mapKeys { (k, _) -> k.toInt() },
        nedsattArbeidsevneEllerStudieevneDato = nedsattArbeidsevneEllerStudieevneDato,
        ytterligereNedsattArbeidsevneDato = ytterligereNedsattArbeidsevneDato,
    )
}

fun tilDomene(
    grunnlagYrkesskadeDTO: GrunnlagYrkesskadeDTO,
    nedsattArbeidsevneEllerStudieevneDato: LocalDate,
    ytterligereNedsattArbeidsevneDato: LocalDate?
): IBeregningsGrunnlag.GrunnlagYrkesskade {
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
        grunnlagEtterYrkesskadeFordel = grunnlagYrkesskadeDTO.grunnlagEtterYrkesskadeFordel,
        nedsattArbeidsevneEllerStudieevneDato = nedsattArbeidsevneEllerStudieevneDato,
        ytterligereNedsattArbeidsevneDato = ytterligereNedsattArbeidsevneDato,
    )
}

fun tilDomene(
    grunnlagUføreDTO: GrunnlagUføreDTO,
    nedsattArbeidsevneEllerStudieevneDato: LocalDate,
    ytterligereNedsattArbeidsevneDato: LocalDate?
): IBeregningsGrunnlag.GrunnlagUføre {
    return IBeregningsGrunnlag.GrunnlagUføre(
        grunnlag = grunnlagUføreDTO.grunnlaget.toDouble(),
        type = grunnlagUføreDTO.type.tilDomene(),
        grunnlag11_19 = grunnlagUføreDTO.grunnlag.tilDomene(
            nedsattArbeidsevneEllerStudieevneDato,
            ytterligereNedsattArbeidsevneDato
        ),
        uføregrader = grunnlagUføreDTO.uføregrader.associate { it.virkningstidspunkt to it.grad },
        uføreYtterligereNedsattArbeidsevneÅr = grunnlagUføreDTO.uføreYtterligereNedsattArbeidsevneÅr,
        uføreInntekterFraForegåendeÅr = grunnlagUføreDTO.uføreInntekterFraForegåendeÅr.mapKeys { (k, _) -> k.toInt() },
        nedsattArbeidsevneEllerStudieevneDato = nedsattArbeidsevneEllerStudieevneDato,
        ytterligereNedsattArbeidsevneDato = ytterligereNedsattArbeidsevneDato,
    )
}

fun tilDomene(beregningsgrunnlagDTO: BeregningsgrunnlagDTO): IBeregningsGrunnlag {
    val grunnlagYrkesskade = beregningsgrunnlagDTO.grunnlagYrkesskade
    @Suppress("LocalVariableName", "VariableNaming") val grunnlag11_19dto =
        beregningsgrunnlagDTO.grunnlag11_19dto
    val grunnlagUføre = beregningsgrunnlagDTO.grunnlagUføre
    val nedsattDato = beregningsgrunnlagDTO.nedsattArbeidsevneEllerStudieevneDato
    val ytterligereDato = beregningsgrunnlagDTO.ytterligereNedsattArbeidsevneDato
    return when {
        grunnlag11_19dto != null -> IBeregningsGrunnlag.Grunnlag_11_19(
            grunnlag11_19dto.grunnlaget,
            grunnlag11_19dto.er6GBegrenset,
            grunnlag11_19dto.erGjennomsnitt,
            grunnlag11_19dto.inntekter.mapKeys { (k, _) -> k.toInt() },
            nedsattArbeidsevneEllerStudieevneDato = nedsattDato,
            ytterligereNedsattArbeidsevneDato = ytterligereDato,
        )

        grunnlagYrkesskade != null -> {
            var beregningsGrunnlag: IBeregningsGrunnlag? = null
            if (grunnlagYrkesskade.beregningsgrunnlag.grunnlagUføre != null) {
                beregningsGrunnlag =
                    grunnlagYrkesskade.beregningsgrunnlag.grunnlagUføre?.let {
                        tilDomene(
                            it,
                            grunnlagYrkesskade.beregningsgrunnlag.nedsattArbeidsevneEllerStudieevneDato,
                            grunnlagYrkesskade.beregningsgrunnlag.ytterligereNedsattArbeidsevneDato
                        )
                    }
            } else if (beregningsgrunnlagDTO.grunnlagYrkesskade?.beregningsgrunnlag?.grunnlagYrkesskade != null) {
                beregningsGrunnlag =
                    grunnlagYrkesskade.beregningsgrunnlag.grunnlagYrkesskade?.let {
                        tilDomene(
                            it,
                            grunnlagYrkesskade.beregningsgrunnlag.nedsattArbeidsevneEllerStudieevneDato,
                            grunnlagYrkesskade.beregningsgrunnlag.ytterligereNedsattArbeidsevneDato
                        )
                    }
            } else if (grunnlagYrkesskade.beregningsgrunnlag.grunnlag11_19dto != null) {
                beregningsGrunnlag =
                    grunnlagYrkesskade.beregningsgrunnlag.grunnlag11_19dto?.tilDomene(
                        grunnlagYrkesskade.beregningsgrunnlag.nedsattArbeidsevneEllerStudieevneDato,
                        grunnlagYrkesskade.beregningsgrunnlag.ytterligereNedsattArbeidsevneDato,
                    )
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
                nedsattArbeidsevneEllerStudieevneDato = nedsattDato,
                ytterligereNedsattArbeidsevneDato = ytterligereDato,
            )
        }

        grunnlagUføre != null -> IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag11_19 = grunnlagUføre.grunnlag.tilDomene(nedsattDato, ytterligereDato),
            uføreInntekterFraForegåendeÅr = grunnlagUføre.uføreInntekterFraForegåendeÅr.mapKeys { (k, _) -> k.toInt() },
            uføreYtterligereNedsattArbeidsevneÅr = grunnlagUføre.uføreYtterligereNedsattArbeidsevneÅr,
            uføregrader = grunnlagUføre.uføregrader.associate { it.virkningstidspunkt to it.grad },
            grunnlag = grunnlagUføre.grunnlaget.toDouble(),
            type = grunnlagUføre.type.tilDomene(),
            nedsattArbeidsevneEllerStudieevneDato = nedsattDato,
            ytterligereNedsattArbeidsevneDato = ytterligereDato,
        )

        else -> error("Ugyldig tilstand.")
    }
}

private fun UføreType.tilDomene(): no.nav.aap.statistikk.avsluttetbehandling.UføreType =
    when (this) {
        UføreType.STANDARD -> no.nav.aap.statistikk.avsluttetbehandling.UføreType.STANDARD
        UføreType.YTTERLIGERE_NEDSATT -> no.nav.aap.statistikk.avsluttetbehandling.UføreType.YTTERLIGERE_NEDSATT
    }