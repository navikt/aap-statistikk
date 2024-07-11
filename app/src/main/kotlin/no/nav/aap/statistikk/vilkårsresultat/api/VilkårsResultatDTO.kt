package no.nav.aap.statistikk.vilkårsresultat.api

import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import java.time.LocalDate

data class VilkårsResultatDTO(val saksnummer: String, val typeBehandling: String, val vilkår: List<VilkårDTO>) {
    fun tilDomene(): Vilkårsresultat {
        return Vilkårsresultat(
            saksnummer = this.saksnummer,
            behandlingsType = this.typeBehandling,
            vilkår = this.vilkår.map { it.tilDomene() }
        )
    }
}

data class VilkårDTO(val vilkårType: String, val perioder: List<VilkårsPeriodeDTO>) {
    fun tilDomene(): Vilkår {
        return Vilkår(
            vilkårType = this.vilkårType,
            perioder = this.perioder.map { it.tilDomene() }
        )
    }
}

data class VilkårsPeriodeDTO(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val utfall: String,
    val manuellVurdering: Boolean,
    val innvilgelsesårsak: String?,
    val avslagsårsak: String?
) {
    fun tilDomene(): VilkårsPeriode {
        return VilkårsPeriode(
            fraDato = this.fraDato,
            tilDato = this.tilDato,
            utfall = this.utfall,
            manuellVurdering = this.manuellVurdering,
            innvilgelsesårsak = this.innvilgelsesårsak,
            avslagsårsak = this.avslagsårsak
        )
    }
}