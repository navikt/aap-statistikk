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
            tilkjentYtelse = tilkjentYtelse.tilDomene(
                sakId,
                behandlingsReferanse = UUID.fromString(behandlingsReferanse)
            ),
            vilkårsresultat = vilkårsResultat.tilDomene(UUID.fromString(behandlingsReferanse))
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