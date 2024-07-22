package no.nav.aap.statistikk.vilkårsresultat

import java.time.LocalDate

data class Vilkårsresultat(
    val saksnummer: String,
    val behandlingsReferanse: String,
    val behandlingsType: String,
    val vilkår: List<Vilkår>
)

data class Vilkår(val vilkårType: String, val perioder: List<VilkårsPeriode>)

data class VilkårsPeriode(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val utfall: String,
    val manuellVurdering: Boolean,
    // Er disse interessante? Er de strenger?
    val innvilgelsesårsak: String? = null,
    val avslagsårsak: String? = null
)
