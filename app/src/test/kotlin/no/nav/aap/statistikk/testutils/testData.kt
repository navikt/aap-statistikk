package no.nav.aap.statistikk.testutils

import no.nav.aap.statistikk.api_kontrakt.AvsluttetBehandlingDTO
import no.nav.aap.statistikk.api_kontrakt.BeregningsgrunnlagDTO
import no.nav.aap.statistikk.api_kontrakt.Grunnlag11_19DTO
import no.nav.aap.statistikk.api_kontrakt.GrunnlagYrkesskadeDTO
import no.nav.aap.statistikk.api_kontrakt.TilkjentYtelseDTO
import no.nav.aap.statistikk.api_kontrakt.TilkjentYtelsePeriodeDTO
import no.nav.aap.statistikk.api_kontrakt.Utfall
import no.nav.aap.statistikk.api_kontrakt.VilkårDTO
import no.nav.aap.statistikk.api_kontrakt.VilkårsPeriodeDTO
import no.nav.aap.statistikk.api_kontrakt.VilkårsResultatDTO
import no.nav.aap.statistikk.api_kontrakt.Vilkårtype
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID


fun avsluttetBehandlingDTO(referanse: UUID, saksnummer: String): AvsluttetBehandlingDTO {
    return AvsluttetBehandlingDTO(
        behandlingsReferanse = referanse,
        saksnummer = saksnummer,
        tilkjentYtelse = TilkjentYtelseDTO(
            perioder = listOf(
                TilkjentYtelsePeriodeDTO(
                    fraDato = LocalDate.now().minusYears(1),
                    tilDato = LocalDate.now().plusDays(1),
                    dagsats = 1337.420,
                    gradering = 90.0
                ),
                TilkjentYtelsePeriodeDTO(
                    fraDato = LocalDate.now().minusYears(3),
                    tilDato = LocalDate.now().minusYears(2),
                    dagsats = 1234.0,
                    gradering = 45.0
                )
            )
        ),
        vilkårsResultat = VilkårsResultatDTO(
            typeBehandling = "førstegangsbehandling",
            vilkår = listOf(
                VilkårDTO(
                    vilkårType = Vilkårtype.ALDERSVILKÅRET, perioder = listOf(
                        VilkårsPeriodeDTO(
                            fraDato = LocalDate.now().minusYears(2),
                            tilDato = LocalDate.now().plusDays(3),
                            manuellVurdering = false,
                            utfall = Utfall.OPPFYLT
                        )
                    )
                )
            )
        ),
        beregningsGrunnlag = BeregningsgrunnlagDTO(
            grunnlagYrkesskade = GrunnlagYrkesskadeDTO(
                grunnlaget = BigDecimal(25000.0),
                inkludererUføre = false,
                beregningsgrunnlag = BeregningsgrunnlagDTO(
                    grunnlag11_19dto = Grunnlag11_19DTO(
                        inntekter = mapOf("2019" to 25000.0, "2020" to 26000.0),
                        grunnlaget = 20000.0,
                        er6GBegrenset = false,
                        erGjennomsnitt = true,
                    )
                ),
                terskelverdiForYrkesskade = 70,
                andelSomSkyldesYrkesskade = BigDecimal(30),
                andelYrkesskade = 25,
                benyttetAndelForYrkesskade = 20,
                andelSomIkkeSkyldesYrkesskade = BigDecimal(40),
                antattÅrligInntektYrkesskadeTidspunktet = BigDecimal(25000),
                yrkesskadeTidspunkt = 2018,
                grunnlagForBeregningAvYrkesskadeandel = BigDecimal(25000),
                yrkesskadeinntektIG = BigDecimal(6),
                grunnlagEtterYrkesskadeFordel = BigDecimal(25000)
            ),
        )
    )
}