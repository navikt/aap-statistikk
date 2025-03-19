package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.behandling.BQYtelseBehandling
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsGrunnlagBQ
import no.nav.aap.statistikk.sak.BQBehandling
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat

interface IBQSakstatistikkRepository {
    fun lagre(payload: BQBehandling)
}

interface IBQYtelsesstatistikkRepository {
    fun lagre(payload: Vilkårsresultat)
    fun lagre(payload: TilkjentYtelse)
    fun lagre(payload: BeregningsGrunnlagBQ)
    fun lagre(payload: BQYtelseBehandling)
}