package no.nav.aap.statistikk.vilkårsresultat.repository

interface IVilkårsresultatRepository {
    fun lagreVilkårsResultat(vilkårsresultat: VilkårsResultatEntity): Int
    fun hentVilkårsResultat(vilkårResultatId: Int): VilkårsResultatEntity?
}