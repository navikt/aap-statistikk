package no.nav.aap.statistikk.vilkårsresultat.repository

import no.nav.aap.statistikk.behandling.BehandlingId
import java.util.*

interface IVilkårsresultatRepository {
    fun lagreVilkårsResultat(
        vilkårsresultat: VilkårsResultatEntity,
        behandlingId: BehandlingId
    ): Long

    fun hentVilkårsResultat(vilkårResultatId: Long): VilkårsResultatEntity?

    fun hentForBehandling(behandlingsReferanse: UUID): VilkårsResultatEntity
}