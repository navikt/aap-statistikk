package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat

data class AvsluttetBehandling(val tilkjentYtelse: List<TilkjentYtelsePeriode>, val vilkårsresultat: Vilkårsresultat)