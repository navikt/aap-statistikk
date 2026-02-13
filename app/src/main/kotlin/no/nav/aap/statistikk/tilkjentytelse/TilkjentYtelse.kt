package no.nav.aap.statistikk.tilkjentytelse

import no.nav.aap.statistikk.sak.Saksnummer
import java.time.LocalDate
import java.util.*

data class TilkjentYtelsePeriode(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val dagsats: Double,
    val gradering: Double,
    val redusertDagsats: Double,
    val antallBarn: Int = 0,
    val barnetilleggSats: Double,
    val barnetillegg: Double,
    val utbetalingsdato: LocalDate,
    val minsteSats: Minstesats
)

data class TilkjentYtelse(
    val saksnummer: Saksnummer,
    val behandlingsReferanse: UUID,
    val perioder: List<TilkjentYtelsePeriode>
) {
    fun begrensPerioderTil(vedtaksdato: LocalDate): TilkjentYtelse {
        return this.copy(perioder = perioder.filter { it.utbetalingsdato <= vedtaksdato })
    }
}

enum class Minstesats { IKKE_MINSTESATS, MINSTESATS_OVER_25, MINSTESATS_UNDER_25 }