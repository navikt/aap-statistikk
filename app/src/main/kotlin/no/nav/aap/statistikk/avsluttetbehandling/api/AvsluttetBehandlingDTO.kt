package no.nav.aap.statistikk.avsluttetbehandling.api

import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import no.nav.aap.statistikk.vilkårsresultat.api.VilkårsResultatDTO
import java.math.BigDecimal
import java.time.LocalDate

data class AvsluttetBehandlingDTO(
    val tilkjentYtelse: List<TilkjentYtelsePeriodeDTO>,
    val vilkårsResultat: VilkårsResultatDTO
) {
    fun tilDomene(): AvsluttetBehandling {
        return AvsluttetBehandling(
            tilkjentYtelse = tilkjentYtelse.map(TilkjentYtelsePeriodeDTO::tilDomene),
            vilkårsresultat = vilkårsResultat.tilDomene()
        )
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