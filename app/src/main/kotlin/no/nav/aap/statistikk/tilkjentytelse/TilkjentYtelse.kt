package no.nav.aap.statistikk.tilkjentytelse

import java.time.LocalDate
import java.util.*

data class TilkjentYtelsePeriode(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val dagsats: Double,
    val gradering: Double
)

data class TilkjentYtelse(
    val saksnummer: String,
    val behandlingsReferanse: UUID,
    val perioder: List<TilkjentYtelsePeriode>
)