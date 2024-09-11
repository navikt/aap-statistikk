package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import java.util.UUID

interface IBQRepository {
    fun lagre(payload: Vilkårsresultat)
    fun lagre(payload: TilkjentYtelse)
    fun lagre(payload: IBeregningsGrunnlag, behandlingsReferanse: UUID)
}