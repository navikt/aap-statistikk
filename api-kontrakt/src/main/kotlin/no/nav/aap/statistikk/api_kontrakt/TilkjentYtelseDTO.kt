package no.nav.aap.statistikk.api_kontrakt

import java.time.LocalDate

/**
 * Liste over perioder med tilkjent ytelse.
 */
public data class TilkjentYtelseDTO(
    val perioder: List<TilkjentYtelsePeriodeDTO>
)

public data class TilkjentYtelsePeriodeDTO(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val dagsats: Double,
    val gradering: Double,
)
