package no.nav.aap.statistikk.avsluttetbehandling.api

import no.nav.aap.statistikk.api_kontrakt.VilkårDTO
import no.nav.aap.statistikk.api_kontrakt.VilkårsPeriodeDTO
import no.nav.aap.statistikk.api_kontrakt.VilkårsResultatDTO
import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import java.util.*

fun VilkårsResultatDTO.tilDomene(saksnummer: String, behandlingsReferanse: UUID): Vilkårsresultat {
    return Vilkårsresultat(saksnummer = saksnummer,
        behandlingsType = this.typeBehandling,
        behandlingsReferanse = behandlingsReferanse,
        vilkår = this.vilkår.map { it.tilDomene() })
}

fun VilkårDTO.tilDomene(): Vilkår {
    return Vilkår(vilkårType = this.vilkårType, perioder = this.perioder.map { it.tilDomene() })
}

fun VilkårsPeriodeDTO.tilDomene(): VilkårsPeriode {
    return VilkårsPeriode(
        fraDato = this.fraDato,
        tilDato = this.tilDato,
        utfall = this.utfall.toString(),
        manuellVurdering = this.manuellVurdering,
        innvilgelsesårsak = this.innvilgelsesårsak,
        avslagsårsak = this.avslagsårsak
    )
}