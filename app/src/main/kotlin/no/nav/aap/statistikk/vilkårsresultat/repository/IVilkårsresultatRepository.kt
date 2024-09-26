package no.nav.aap.statistikk.vilkårsresultat.repository

import no.nav.aap.statistikk.behandling.BehandlingId

interface IVilkårsresultatRepository {
    fun lagreVilkårsResultat(
        vilkårsresultat: VilkårsResultatEntity,
        behandlingId: BehandlingId
    ): Long

    fun hentVilkårsResultat(vilkårResultatId: Long): VilkårsResultatEntity?
}