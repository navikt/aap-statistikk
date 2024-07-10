package no.nav.aap.statistikk.api

import java.time.LocalDate

data class VilkårsResultatDTO(val saksnummer: String, val typeBehandling: String, val vilkår: List<VilkårDTO>)

data class VilkårDTO(val vilkårType: String, val perioder: List<VilkårsPeriodeDTO>)

data class VilkårsPeriodeDTO(
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val utfall: String,
    val manuellVurdering: Boolean,
    val innvilgelsesårsak: String?,
    val avslagsårsak: String?
)