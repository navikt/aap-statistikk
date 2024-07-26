package no.nav.aap.statistikk.avsluttetbehandling.api

import com.papsign.ktor.openapigen.annotations.type.string.example.StringExample
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import java.time.LocalDate
import java.util.*

data class AvsluttetBehandlingDTO(
    @StringExample("4LELS7K", "4LEFCQ8") val saksnummer: String,
    val behandlingsReferanse: UUID,
    val tilkjentYtelse: TilkjentYtelseDTO,
    val vilkårsResultat: VilkårsResultatDTO,
    val beregningsGrunnlag: Any
) {
    fun tilDomene(): AvsluttetBehandling {
        return AvsluttetBehandling(
            tilkjentYtelse = tilkjentYtelse.tilDomene(
                saksnummer,
                behandlingsReferanse = behandlingsReferanse
            ),
            vilkårsresultat = vilkårsResultat.tilDomene(saksnummer, behandlingsReferanse)
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