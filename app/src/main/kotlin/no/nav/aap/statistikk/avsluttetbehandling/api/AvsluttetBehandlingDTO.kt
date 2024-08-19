package no.nav.aap.statistikk.avsluttetbehandling.api

import com.papsign.ktor.openapigen.annotations.Request
import com.papsign.ktor.openapigen.annotations.type.`object`.example.ExampleProvider
import com.papsign.ktor.openapigen.annotations.type.`object`.example.WithExample
import com.papsign.ktor.openapigen.annotations.type.string.example.StringExample
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.UføreType
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import no.nav.aap.statistikk.vilkårsresultat.Vilkårtype
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

val eksempelUUID = UUID.randomUUID()

@Request("Ved avsluttet behandling sendes samlet data.")
@WithExample
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

    companion object : ExampleProvider<AvsluttetBehandlingDTO> {
        override val example: AvsluttetBehandlingDTO =
            AvsluttetBehandlingDTO(
                saksnummer = "4LELS7K",
                behandlingsReferanse = eksempelUUID,
                tilkjentYtelse = TilkjentYtelseDTO(
                    perioder = listOf(
                        TilkjentYtelsePeriodeDTO(
                            fraDato = LocalDate.now(),
                            tilDato = LocalDate.now().plusYears(1),
                            dagsats = 1000.0,
                            gradering = 0.0
                        )
                    )
                ),
                vilkårsResultat = VilkårsResultatDTO(
                    typeBehandling = "Førstegangsbehandling",
                    vilkår = listOf(
                        VilkårDTO(
                            Vilkårtype.GRUNNLAGET, perioder = listOf(
                                VilkårsPeriodeDTO(
                                    fraDato = LocalDate.now().minusWeeks(2),
                                    tilDato = LocalDate.now(),
                                    utfall = Utfall.OPPFYLT,
                                    manuellVurdering = true
                                )
                            )
                        )
                    )
                ),
                beregningsGrunnlag = BeregningsgrunnlagDTO(
                    grunnlagYrkesskade = GrunnlagYrkesskadeDTO(
                        grunnlaget = BigDecimal.valueOf(100000),
                        beregningsgrunnlag = BeregningsgrunnlagDTO(
                            grunnlag11_19dto = Grunnlag11_19DTO(
                                inntekter = mapOf(
                                    "2021" to BigDecimal.valueOf(100000),
                                    "2022" to BigDecimal.valueOf(10000),
                                    "2023" to BigDecimal.valueOf(1000),
                                ),
                                grunnlaget = 5.5,
                                er6GBegrenset = false,
                                erGjennomsnitt = false,
                            )
                        ),
                        terskelverdiForYrkesskade = 70,
                        andelSomSkyldesYrkesskade = BigDecimal.valueOf(60),
                        andelYrkesskade = 60,
                        benyttetAndelForYrkesskade = 60,
                        andelSomIkkeSkyldesYrkesskade = BigDecimal.valueOf(40),
                        inkludererUføre = false,
                        antattÅrligInntektYrkesskadeTidspunktet = BigDecimal.valueOf(500000),
                        yrkesskadeTidspunkt = 1999,
                        grunnlagForBeregningAvYrkesskadeandel = BigDecimal.valueOf(10000),
                        yrkesskadeinntektIG = BigDecimal.valueOf(100000),
                        grunnlagEtterYrkesskadeFordel = BigDecimal.valueOf(100000),
                    )
                )
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
        return TilkjentYtelsePeriode(
            fraDato = fraDato,
            tilDato = tilDato,
            dagsats = dagsats,
            gradering
        )
    }
}

data class Grunnlag11_19DTO(
    val inntekter: Map<String, BigDecimal>,
    val grunnlaget: Double,
    val er6GBegrenset: Boolean,
    val erGjennomsnitt: Boolean
) {
    fun tilDomene(
    ): IBeregningsGrunnlag.Grunnlag_11_19 {
        return IBeregningsGrunnlag.Grunnlag_11_19(
            grunnlag = grunnlaget,
            er6GBegrenset = er6GBegrenset,
            erGjennomsnitt = erGjennomsnitt,
            inntekter = inntekter
        )
    }
}

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
    val grunnlagEtterYrkesskadeFordel: BigDecimal,
) {
    fun tilDomene(): IBeregningsGrunnlag.GrunnlagYrkesskade {
        return IBeregningsGrunnlag.GrunnlagYrkesskade(
            grunnlaget = grunnlaget.toDouble(),
            er6GBegrenset =
            beregningsgrunnlag.grunnlagYrkesskade?.beregningsgrunnlag?.grunnlag11_19dto?.er6GBegrenset
                ?: false,
            beregningsgrunnlag = beregningsgrunnlag.tilDomene(), // Assuming recursive structure handling
            terskelverdiForYrkesskade = terskelverdiForYrkesskade,
            andelSomSkyldesYrkesskade = andelSomSkyldesYrkesskade,
            andelYrkesskade = andelYrkesskade,
            benyttetAndelForYrkesskade = benyttetAndelForYrkesskade,
            andelSomIkkeSkyldesYrkesskade = andelSomIkkeSkyldesYrkesskade,
            antattÅrligInntektYrkesskadeTidspunktet = antattÅrligInntektYrkesskadeTidspunktet,
            yrkesskadeTidspunkt = yrkesskadeTidspunkt,
            grunnlagForBeregningAvYrkesskadeandel = grunnlagForBeregningAvYrkesskadeandel,
            yrkesskadeinntektIG = yrkesskadeinntektIG,
            grunnlagEtterYrkesskadeFordel = grunnlagEtterYrkesskadeFordel
        )
    }
}

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
    val uføreInntekterFraForegåendeÅr: Map<String, BigDecimal>,
    val uføreInntektIKroner: BigDecimal,
    val uføreYtterligereNedsattArbeidsevneÅr: Int,
) {
    fun tilDomene(): IBeregningsGrunnlag.GrunnlagUføre {
        return IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag = grunnlaget.toDouble(),
            type = type,
            grunnlag11_19 = grunnlag.tilDomene(),
            uføregrad = uføregrad,
            uføreInntektIKroner = uføreInntektIKroner,
            uføreYtterligereNedsattArbeidsevneÅr = uføreYtterligereNedsattArbeidsevneÅr,
            er6GBegrenset = grunnlag.er6GBegrenset, // ?,
            uføreInntekterFraForegåendeÅr = uføreInntekterFraForegåendeÅr
        )
    }
}

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

    fun tilDomene(): IBeregningsGrunnlag {
        if (grunnlag11_19dto != null) {
            return IBeregningsGrunnlag.Grunnlag_11_19(
                grunnlag11_19dto.grunnlaget,
                grunnlag11_19dto.er6GBegrenset,
                grunnlag11_19dto.erGjennomsnitt,
                grunnlag11_19dto.inntekter,
            )
        }
        if (grunnlagYrkesskade != null) {
            var beregningsGrunnlag: IBeregningsGrunnlag? = null;
            if (grunnlagYrkesskade.beregningsgrunnlag.grunnlagUføre != null) {
                beregningsGrunnlag = grunnlagYrkesskade.beregningsgrunnlag.grunnlagUføre.tilDomene()
            } else if (grunnlagYrkesskade.beregningsgrunnlag.grunnlagYrkesskade != null) {
                beregningsGrunnlag =
                    grunnlagYrkesskade.beregningsgrunnlag.grunnlagYrkesskade.tilDomene()
            } else if (grunnlagYrkesskade.beregningsgrunnlag.grunnlag11_19dto != null) {
                beregningsGrunnlag =
                    grunnlagYrkesskade.beregningsgrunnlag.grunnlag11_19dto.tilDomene()
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
                uføreInntekterFraForegåendeÅr = grunnlagUføre.uføreInntekterFraForegåendeÅr,
                uføreYtterligereNedsattArbeidsevneÅr = grunnlagUføre.uføreYtterligereNedsattArbeidsevneÅr,
                uføregrad = grunnlagUføre.uføregrad,
                grunnlag = grunnlagUføre.grunnlaget.toDouble(),
                er6GBegrenset = grunnlagUføre.grunnlag.er6GBegrenset,
                type = grunnlagUføre.type
            )
        }
        throw IllegalStateException()
    }
}