package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat

class BQRepository(
    private val client: BigQueryClient
) {
    private val vilkårsVurderingTabell = VilkårsVurderingTabell()
    private val tilkjentYtelseTabell = TilkjentYtelseTabell()

    fun lagre(payload: Vilkårsresultat) {
        client.create(vilkårsVurderingTabell)
        client.insert(vilkårsVurderingTabell, payload)
    }

    fun lagre(payload: TilkjentYtelse) {
        client.create(tilkjentYtelseTabell)
        client.insert(tilkjentYtelseTabell, payload)
    }

    override fun toString(): String {
        return "BQRepository(client=$client, vilkårsVurderingTabell=$vilkårsVurderingTabell)"
    }
}