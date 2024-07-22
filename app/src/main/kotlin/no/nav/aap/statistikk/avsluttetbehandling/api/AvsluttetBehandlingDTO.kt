package no.nav.aap.statistikk.avsluttetbehandling.api

import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import no.nav.aap.statistikk.vilkårsresultat.api.VilkårsResultatDTO
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

data class AvsluttetBehandlingDTO(
    val sakId: String,
    val behandlingsReferanse: String,
    val tilkjentYtelse: TilkjentYtelseDTO,
    val vilkårsResultat: VilkårsResultatDTO
) {
    fun tilDomene(): AvsluttetBehandling {
        return AvsluttetBehandling(
            sakId = sakId,
            behandlingReferanse = UUID.fromString(behandlingsReferanse),
            tilkjentYtelse = tilkjentYtelse.tilDomene(),
            vilkårsresultat = vilkårsResultat.tilDomene(behandlingsReferanse)
        )
    }
}

data class TilkjentYtelseDTO(val perioder: List<TilkjentYtelsePeriodeDTO>) {
    fun tilDomene(): TilkjentYtelse {
        return TilkjentYtelse(perioder = perioder.map(TilkjentYtelsePeriodeDTO::tilDomene))
    }
}

data class TilkjentYtelsePeriodeDTO(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val dagsats: BigDecimal,
    val gradering: BigDecimal,
) {
    fun tilDomene(): TilkjentYtelsePeriode {
        return TilkjentYtelsePeriode(fraDato = fraDato, tilDato = tilDato, dagsats = dagsats, gradering)
    }
}