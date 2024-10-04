package no.nav.aap.statistikk.api_kontrakt

import java.time.LocalDate

public data class VilkårsResultatDTO(
    val typeBehandling: String, val vilkår: List<VilkårDTO>
)

public data class VilkårDTO(val vilkårType: Vilkårtype, val perioder: List<VilkårsPeriodeDTO>) {
}


public enum class Utfall {
    IKKE_VURDERT, IKKE_RELEVANT, OPPFYLT, IKKE_OPPFYLT
}

public enum class Vilkårtype {
    ALDERSVILKÅRET,
    SYKDOMSVILKÅRET,
    BISTANDSVILKÅRET, MEDLEMSKAP,
    GRUNNLAGET,
    SYKEPENGEERSTATNING
}

public data class VilkårsPeriodeDTO(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val utfall: Utfall,
    val manuellVurdering: Boolean,
    val innvilgelsesårsak: String? = null,
    val avslagsårsak: String? = null
)