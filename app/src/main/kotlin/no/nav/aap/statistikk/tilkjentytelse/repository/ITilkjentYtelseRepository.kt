package no.nav.aap.statistikk.tilkjentytelse.repository

import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import java.util.UUID

interface ITilkjentYtelseRepository : Repository {
    fun lagreTilkjentYtelse(tilkjentYtelse: TilkjentYtelseEntity): Long
    fun hentTilkjentYtelse(tilkjentYtelseId: Int): TilkjentYtelse
    fun hentForBehandling(behandlingId: UUID): TilkjentYtelse?
}