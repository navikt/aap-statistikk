package no.nav.aap.statistikk.vilkårsresultat

import no.nav.aap.statistikk.behandling.TypeBehandling
import java.time.LocalDate
import java.util.*

data class Vilkårsresultat(
    val saksnummer: String,
    val behandlingsReferanse: UUID,
    val behandlingsType: TypeBehandling,
    val vilkår: List<Vilkår>
)


data class Vilkår(val vilkårType: Vilkårtype, val perioder: List<VilkårsPeriode>) {
    fun harPerioderSomErOppfylt(): Boolean {
        return perioder.any { it.utfall == Utfall.OPPFYLT }
    }
}

data class VilkårsPeriode(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val utfall: Utfall,
    val manuellVurdering: Boolean,
    // Er disse interessante? Er de strenger?
    val innvilgelsesårsak: String? = null,
    val avslagsårsak: String? = null
)

enum class Vilkårtype {
    ALDERSVILKÅRET,
    SYKDOMSVILKÅRET,
    BISTANDSVILKÅRET, MEDLEMSKAP,
    GRUNNLAGET,
    SYKEPENGEERSTATNING,
    LOVVALG
}

enum class Utfall {
    IKKE_VURDERT, IKKE_RELEVANT, OPPFYLT, IKKE_OPPFYLT
}

// endringslogikk
// lastet opp-tidspunkt på bigquery
// sjekk grunnlaget - både oppad og nedad begrenset
