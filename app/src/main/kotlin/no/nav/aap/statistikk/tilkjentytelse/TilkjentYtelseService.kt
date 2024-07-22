package no.nav.aap.statistikk.tilkjentytelse

import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseEntity
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository

class TilkjentYtelseService(private val tilkjentYtelseRepository: TilkjentYtelseRepository) {
    fun lagreTilkjentYtelse(tilkjentYtelse: TilkjentYtelse) {
        tilkjentYtelseRepository.lagreTilkjentYtelse(TilkjentYtelseEntity.fraDomene(tilkjentYtelse))
    }
}