package no.nav.aap.statistikk.tilkjentytelse

import no.nav.aap.statistikk.bigquery.BQRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseEntity
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository

class TilkjentYtelseService(
    private val tilkjentYtelseRepository: TilkjentYtelseRepository,
    private val bqRepository: BQRepository
) {
    fun lagreTilkjentYtelse(tilkjentYtelse: TilkjentYtelse) {
        tilkjentYtelseRepository.lagreTilkjentYtelse(TilkjentYtelseEntity.fraDomene(tilkjentYtelse))
        bqRepository.lagre(tilkjentYtelse)
    }
}