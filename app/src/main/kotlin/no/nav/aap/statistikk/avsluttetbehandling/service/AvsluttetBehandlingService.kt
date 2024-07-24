package no.nav.aap.statistikk.avsluttetbehandling.service

import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelseService
import no.nav.aap.statistikk.vilkårsresultat.VilkårsResultatService

class AvsluttetBehandlingService(
    private val vilkårsResultatService: VilkårsResultatService,
    private val tilkjentYtelseRepository: TilkjentYtelseService
) {
    fun lagre(avsluttetBehandling: AvsluttetBehandling) {
        vilkårsResultatService.mottaVilkårsResultat(
            avsluttetBehandling.vilkårsresultat
        )

        tilkjentYtelseRepository.lagreTilkjentYtelse(avsluttetBehandling.tilkjentYtelse)
    }
}