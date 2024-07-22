package no.nav.aap.statistikk.tilkjentytelse.repository

import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import java.math.BigDecimal
import java.time.LocalDate


data class TilkjentYtelseEntity(val id: Int? = null, val perioder: List<TilkjentYtelsePeriodeEntity>) {
    companion object {
        fun fraDomene(tilkjentYtelse: TilkjentYtelse): TilkjentYtelseEntity {
            return TilkjentYtelseEntity(perioder = tilkjentYtelse.perioder.map {
                TilkjentYtelsePeriodeEntity.fraDomene(
                    it
                )
            })
        }
    }
}

data class TilkjentYtelsePeriodeEntity(
    val id: Int? = null,
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val dagsats: BigDecimal,
    val gradering: BigDecimal,
) {
    companion object {
        fun fraDomene(tilkjentYtelsePeriode: TilkjentYtelsePeriode): TilkjentYtelsePeriodeEntity {
            return TilkjentYtelsePeriodeEntity(
                fraDato = tilkjentYtelsePeriode.fraDato,
                tilDato = tilkjentYtelsePeriode.tilDato,
                dagsats = tilkjentYtelsePeriode.dagsats,
                gradering = tilkjentYtelsePeriode.gradering,
            )
        }
    }
}