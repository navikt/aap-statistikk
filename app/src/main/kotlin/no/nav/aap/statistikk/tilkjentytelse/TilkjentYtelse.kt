package no.nav.aap.statistikk.tilkjentytelse

import no.nav.aap.statistikk.sak.Saksnummer
import java.time.LocalDate
import java.util.*

data class TilkjentYtelsePeriode(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val dagsats: Double,
    val gradering: Double,
    val redusertDagsats: Double = dagsats * gradering / 100.0,
    val antallBarn: Int = 0,
    val barnetilleggSats: Double = 37.0,
    val barnetillegg: Double = 0.0
)

data class TilkjentYtelse(
    val saksnummer: Saksnummer,
    val behandlingsReferanse: UUID,
    val perioder: List<TilkjentYtelsePeriode>
)