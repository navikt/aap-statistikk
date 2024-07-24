package no.nav.aap.statistikk.vilkårsresultat

import java.time.LocalDate
import java.util.*

data class Vilkårsresultat(
    val saksnummer: String,
    val behandlingsReferanse: UUID,
    val behandlingsType: String,
    val vilkår: List<Vilkår>
)

enum class Vilkårtype {
    ALDERSVILKÅRET,
    SYKDOMSVILKÅRET,
    BISTANDSVILKÅRET, MEDLEMSKAP,
    GRUNNLAGET,
    SYKEPENGEERSTATNING
}

data class Vilkår(val vilkårType: Vilkårtype, val perioder: List<VilkårsPeriode>)

data class VilkårsPeriode(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val utfall: String,
    val manuellVurdering: Boolean,
    // Er disse interessante? Er de strenger?
    val innvilgelsesårsak: String? = null,
    val avslagsårsak: String? = null
)
