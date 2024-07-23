package no.nav.aap.statistikk.vilkårsresultat.api

import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import java.time.LocalDate
import java.util.*

data class VilkårsResultatDTO(
    val saksnummer: String,
    val typeBehandling: String,
    val vilkår: List<VilkårDTO>
) {
    fun tilDomene(behandlingsReferanse: UUID): Vilkårsresultat {
        return Vilkårsresultat(
            saksnummer = this.saksnummer,
            behandlingsType = this.typeBehandling,
            behandlingsReferanse = behandlingsReferanse,
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

enum class Utfall {
    IKKE_VURDERT,
    IKKE_RELEVANT,
    OPPFYLT,
    IKKE_OPPFYLT
}

data class VilkårsPeriodeDTO(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val utfall: Utfall,
    val manuellVurdering: Boolean,
    val innvilgelsesårsak: String? = null,
    val avslagsårsak: String? = null
) {
    fun tilDomene(): VilkårsPeriode {
        return VilkårsPeriode(
            fraDato = this.fraDato,
            tilDato = this.tilDato,
            utfall = this.utfall.toString(),
            manuellVurdering = this.manuellVurdering,
            innvilgelsesårsak = this.innvilgelsesårsak,
            avslagsårsak = this.avslagsårsak
        )
    }
}