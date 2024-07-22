package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import java.util.*

data class AvsluttetBehandling(
    val sakId: String,
    val behandlingReferanse: UUID,
    val tilkjentYtelse: List<TilkjentYtelsePeriode>,
    val vilkårsresultat: Vilkårsresultat
)