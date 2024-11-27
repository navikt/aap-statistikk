package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.VilkårDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.VilkårsPeriodeDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.VilkårsResultatDTO
import no.nav.aap.statistikk.vilkårsresultat.*
import java.util.*

fun VilkårsResultatDTO.tilDomene(saksnummer: String, behandlingsReferanse: UUID): Vilkårsresultat {
    return Vilkårsresultat(saksnummer = saksnummer,
        behandlingsType = this.typeBehandling.tilDomene(),
        behandlingsReferanse = behandlingsReferanse,
        vilkår = this.vilkår.map { it.tilDomene() })
}

private fun VilkårDTO.tilDomene(): Vilkår {
    return Vilkår(
        vilkårType = this.vilkårType.tilDomene(),
        perioder = this.perioder.map { it.tilDomene() })
}

private fun VilkårsPeriodeDTO.tilDomene(): VilkårsPeriode {
    return VilkårsPeriode(
        fraDato = this.fraDato,
        tilDato = this.tilDato,
        utfall = this.utfall.toString(),
        manuellVurdering = this.manuellVurdering,
        innvilgelsesårsak = this.innvilgelsesårsak,
        avslagsårsak = this.avslagsårsak
    )
}


private fun no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.tilDomene(): Vilkårtype {
    return when (this) {
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.ALDERSVILKÅRET -> Vilkårtype.ALDERSVILKÅRET
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.SYKDOMSVILKÅRET -> Vilkårtype.SYKDOMSVILKÅRET
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.BISTANDSVILKÅRET -> Vilkårtype.BISTANDSVILKÅRET
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.MEDLEMSKAP -> Vilkårtype.MEDLEMSKAP
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.GRUNNLAGET -> Vilkårtype.GRUNNLAGET
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype.SYKEPENGEERSTATNING -> Vilkårtype.SYKEPENGEERSTATNING
    }
}