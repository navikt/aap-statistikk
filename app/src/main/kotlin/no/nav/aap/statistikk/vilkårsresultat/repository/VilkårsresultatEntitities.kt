package no.nav.aap.statistikk.vilkårsresultat.repository

import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import java.time.LocalDate

data class VilkårsResultatEntity(
    val id: Long?,
    val behandlingsReferanse: String,
    val saksnummer: String,
    val typeBehandling: String,
    val vilkår: List<VilkårEntity>
) {
    fun tilVilkårsResultat(): Vilkårsresultat {
        return Vilkårsresultat(
            saksnummer,
            typeBehandling,
            behandlingsReferanse,
            vilkår.map { it: VilkårEntity -> it.tilVilkår() })
    }

    companion object {
        fun fraDomene(behandlingsReferanse: String, vilkårsresultat: Vilkårsresultat): VilkårsResultatEntity {
            return VilkårsResultatEntity(
                null,
                behandlingsReferanse = behandlingsReferanse,
                vilkårsresultat.saksnummer,
                vilkårsresultat.behandlingsType,
                vilkårsresultat.vilkår.map { it: Vilkår -> VilkårEntity.fraDomene(it) }
            )
        }
    }
}

data class VilkårEntity(val id: Long?, val vilkårType: String, val perioder: List<VilkårsPeriodeEntity>) {
    fun tilVilkår(): Vilkår {
        return Vilkår(vilkårType, perioder.map { it.tilVilkårsPeriode() })
    }

    companion object {
        fun fraDomene(vilkår: Vilkår): VilkårEntity {
            return VilkårEntity(
                null,
                vilkår.vilkårType,
                vilkår.perioder.map { it: VilkårsPeriode -> VilkårsPeriodeEntity.fraDomene(it) }
            )
        }
    }
}

data class VilkårsPeriodeEntity(
    val id: Long?,
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val utfall: String,
    val manuellVurdering: Boolean,
    val innvilgelsesårsak: String?,
    val avslagsårsak: String?
) {
    fun tilVilkårsPeriode(): VilkårsPeriode {
        return VilkårsPeriode(fraDato, tilDato, utfall, manuellVurdering, innvilgelsesårsak, avslagsårsak)
    }

    companion object {
        fun fraDomene(vilkårsPeriode: VilkårsPeriode): VilkårsPeriodeEntity {
            return VilkårsPeriodeEntity(
                null,
                vilkårsPeriode.fraDato,
                vilkårsPeriode.tilDato,
                vilkårsPeriode.utfall,
                vilkårsPeriode.manuellVurdering,
                vilkårsPeriode.innvilgelsesårsak,
                vilkårsPeriode.avslagsårsak
            )
        }
    }
}