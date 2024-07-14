package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat

class BQRepository(private val client: BigQueryClient) {

    private val vilkårsVurderingTabell = VilkårsVurderingTabell()

    fun lagreVilkårsResultat(vilkårsresultat: Vilkårsresultat) {
        // Hvis ikke eksiterer, lagre alt
        // Dette er logikk som burde være i servicen egentlig
        client.create(vilkårsVurderingTabell)

        client.insert(vilkårsVurderingTabell, vilkårsresultat)
    }
}