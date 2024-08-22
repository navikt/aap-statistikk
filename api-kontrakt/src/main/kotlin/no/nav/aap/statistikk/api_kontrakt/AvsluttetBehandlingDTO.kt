package no.nav.aap.statistikk.api_kontrakt

import java.util.*

data class AvsluttetBehandlingDTO(
    val saksnummer: String,
    val behandlingsReferanse: UUID,
    val tilkjentYtelse: TilkjentYtelseDTO,
    val vilkårsResultat: VilkårsResultatDTO,
    val beregningsGrunnlag: BeregningsgrunnlagDTO
)