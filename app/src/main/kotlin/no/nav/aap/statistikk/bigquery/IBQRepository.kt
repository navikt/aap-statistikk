package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsGrunnlagBQ
import no.nav.aap.statistikk.sak.BQSak
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import java.util.*

interface IBQRepository {
    fun lagre(payload: Vilkårsresultat)
    fun lagre(payload: TilkjentYtelse)
    fun lagre(payload: BeregningsGrunnlagBQ)
    fun lagre(payload: BQSak)
}