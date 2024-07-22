package no.nav.aap.statistikk.tilkjentytelse

import java.math.BigDecimal
import java.time.LocalDate


data class TilkjentYtelsePeriode(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val dagsats: BigDecimal,
    val gradering: BigDecimal
)

data class TilkjentYtelse(val perioder: List<TilkjentYtelsePeriode>)