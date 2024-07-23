package no.nav.aap.statistikk.avsluttetbehandling.api

import com.papsign.ktor.openapigen.annotations.type.string.example.StringExample
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import no.nav.aap.statistikk.vilkårsresultat.api.VilkårsResultatDTO
import java.time.LocalDate
import java.util.*

data class AvsluttetBehandlingDTO(
    @StringExample("4LELS7K", "4LEFCQ8") val sakId: String,
    val behandlingsReferanse: UUID,
    val tilkjentYtelse: TilkjentYtelseDTO,
    val vilkårsResultat: VilkårsResultatDTO
) {
    fun tilDomene(): AvsluttetBehandling {
        return AvsluttetBehandling(
            tilkjentYtelse = tilkjentYtelse.tilDomene(
                sakId,
                behandlingsReferanse = behandlingsReferanse
            ),
            vilkårsresultat = vilkårsResultat.tilDomene(sakId, behandlingsReferanse)
        )
    }
}

data class TilkjentYtelseDTO(
    val perioder: List<TilkjentYtelsePeriodeDTO>
) {
    fun tilDomene(sakId: String, behandlingsReferanse: UUID): TilkjentYtelse {
        return TilkjentYtelse(
            saksnummer = sakId,
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