package no.nav.aap.statistikk.tilkjentytelse.repository

import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import java.time.LocalDate
import java.util.*


data class TilkjentYtelseEntity(
    val id: Int? = null,
    val saksnummer: Saksnummer,
    val behandlingsReferanse: UUID,
    val perioder: List<TilkjentYtelsePeriodeEntity>
) {
    companion object {
        fun fraDomene(tilkjentYtelse: TilkjentYtelse): TilkjentYtelseEntity {
            return TilkjentYtelseEntity(
                saksnummer = tilkjentYtelse.saksnummer,
                behandlingsReferanse = tilkjentYtelse.behandlingsReferanse,
                perioder = tilkjentYtelse.perioder.map {
                    TilkjentYtelsePeriodeEntity.fraDomene(
                        it
                    )
                }
            )
        }
    }
}

data class TilkjentYtelsePeriodeEntity(
    val id: Int? = null,
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val dagsats: Double,
    val gradering: Double,
    val redusertDagsats: Double = dagsats * gradering / 100.0,
    val antallBarn: Int = 0,
    val barnetilleggSats: Double = 37.0,
    val barnetillegg: Double = 0.0
) {
    companion object {
        fun fraDomene(tilkjentYtelsePeriode: TilkjentYtelsePeriode): TilkjentYtelsePeriodeEntity {
            return TilkjentYtelsePeriodeEntity(
                fraDato = tilkjentYtelsePeriode.fraDato,
                tilDato = tilkjentYtelsePeriode.tilDato,
                dagsats = tilkjentYtelsePeriode.dagsats,
                gradering = tilkjentYtelsePeriode.gradering,
                redusertDagsats = tilkjentYtelsePeriode.redusertDagsats,
                antallBarn = tilkjentYtelsePeriode.antallBarn,
                barnetilleggSats = tilkjentYtelsePeriode.barnetilleggSats,
                barnetillegg = tilkjentYtelsePeriode.barnetillegg,
            )
        }
    }
}