package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat

data class AvsluttetBehandling(
    val tilkjentYtelse: TilkjentYtelse,
    val vilkårsresultat: Vilkårsresultat
)