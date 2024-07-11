package no.nav.aap.statistikk.vilkårsresultat.service

import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import javax.sql.DataSource

class VilkårsResultatService(dataSource: DataSource) {
    private val vilkårsResultatRepository = VilkårsresultatRepository(dataSource)

    fun mottaVilkårsResultat(vilkårsresultat: Vilkårsresultat) {
        vilkårsResultatRepository.lagreVilkårsResultat(VilkårsResultatEntity.fraDomene(vilkårsresultat))
    }
}