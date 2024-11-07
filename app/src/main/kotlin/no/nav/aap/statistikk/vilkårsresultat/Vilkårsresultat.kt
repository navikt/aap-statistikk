package no.nav.aap.statistikk.vilkårsresultat

import java.time.LocalDate
import java.util.*

data class Vilkårsresultat(
    val saksnummer: String,
    val behandlingsReferanse: UUID,
    val behandlingsType: String,
    val vilkår: List<Vilkår>
)


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

enum class Vilkårtype {
    ALDERSVILKÅRET,
    SYKDOMSVILKÅRET,
    BISTANDSVILKÅRET, MEDLEMSKAP,
    GRUNNLAGET,
    SYKEPENGEERSTATNING
}

fun no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.tilDomene(): Vilkårtype {
    return when (this) {
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.ALDERSVILKÅRET -> Vilkårtype.ALDERSVILKÅRET
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.SYKDOMSVILKÅRET -> Vilkårtype.SYKDOMSVILKÅRET
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.BISTANDSVILKÅRET -> Vilkårtype.BISTANDSVILKÅRET
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.MEDLEMSKAP -> Vilkårtype.MEDLEMSKAP
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.GRUNNLAGET -> Vilkårtype.GRUNNLAGET
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.SYKEPENGEERSTATNING -> Vilkårtype.SYKEPENGEERSTATNING
    }
}

// endringslogikk
// lastet opp-tidspunkt på bigquery
// sjekk grunnlaget - både oppad og nedad begrenset
