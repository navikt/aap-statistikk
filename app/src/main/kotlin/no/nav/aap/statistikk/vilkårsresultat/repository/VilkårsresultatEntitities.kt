package no.nav.aap.statistikk.vilkårsresultat.repository

import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.vilkårsresultat.*
import java.time.LocalDate
import java.util.*

data class VilkårsResultatEntity(
    val id: Long?,
    val vilkår: List<VilkårEntity>
) {
    fun tilVilkårsResultat(
        saksnummer: String,
        behandlingsReferanse: UUID,
        typeBehandling: String
    ): Vilkårsresultat {
        return Vilkårsresultat(
            saksnummer,
            behandlingsReferanse,
            TypeBehandling.valueOf(typeBehandling),
            vilkår.map { it: VilkårEntity -> it.tilVilkår() })
    }

    companion object {
        fun fraDomene(vilkårsresultat: Vilkårsresultat): VilkårsResultatEntity {
            return VilkårsResultatEntity(
                null,
                vilkårsresultat.vilkår.map { it: Vilkår -> VilkårEntity.fraDomene(it) }
            )
        }
    }
}

data class VilkårEntity(
    val id: Long?,
    val vilkårType: String,
    val perioder: List<VilkårsPeriodeEntity>
) {
    fun tilVilkår(): Vilkår {
        return Vilkår(Vilkårtype.valueOf(vilkårType), perioder.map { it.tilVilkårsPeriode() })
    }

    companion object {
        fun fraDomene(vilkår: Vilkår): VilkårEntity {
            return VilkårEntity(
                null,
                vilkår.vilkårType.toString(),
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
        return VilkårsPeriode(
            fraDato,
            tilDato,
            Utfall.valueOf(utfall),
            manuellVurdering,
            innvilgelsesårsak,
            avslagsårsak
        )
    }

    companion object {
        fun fraDomene(vilkårsPeriode: VilkårsPeriode): VilkårsPeriodeEntity {
            return VilkårsPeriodeEntity(
                null,
                vilkårsPeriode.fraDato,
                vilkårsPeriode.tilDato,
                vilkårsPeriode.utfall.toString(),
                vilkårsPeriode.manuellVurdering,
                vilkårsPeriode.innvilgelsesårsak,
                vilkårsPeriode.avslagsårsak
            )
        }
    }
}