package no.nav.aap.statistikk.api_kontrakt

import java.util.*

/**
 * @param beregningsGrunnlag Beregningsgrunnlag. Kan være null om behandlingen avsluttes før inntekt hentes inn.
 */
data class AvsluttetBehandlingDTO(
    val saksnummer: String,
    val behandlingsReferanse: UUID,
    val tilkjentYtelse: TilkjentYtelseDTO,
    val vilkårsResultat: VilkårsResultatDTO,
    val beregningsGrunnlag: BeregningsgrunnlagDTO?
)