package no.nav.aap.statistikk.avsluttetbehandling.service

import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.vilkårsresultat.service.VilkårsResultatService

class AvsluttetBehandlingService(private val vilkårsResultatService: VilkårsResultatService) {
    fun lagre(avsluttetBehandling: AvsluttetBehandling) {
        vilkårsResultatService.mottaVilkårsResultat(
            avsluttetBehandling.behandlingReferanse.toString(),
            avsluttetBehandling.vilkårsresultat
        )
    }
}