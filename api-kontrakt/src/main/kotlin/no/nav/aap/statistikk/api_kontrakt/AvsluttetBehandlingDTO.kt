package no.nav.aap.statistikk.api_kontrakt

import java.time.LocalDateTime
import java.util.*

/**
 * @param beregningsGrunnlag Beregningsgrunnlag. Kan være null om behandlingen avsluttes før inntekt hentes inn.
 */
public data class AvsluttetBehandlingDTO(
    val saksnummer: String,
    val behandlingsReferanse: UUID,
    val tilkjentYtelse: TilkjentYtelseDTO,
    val vilkårsResultat: VilkårsResultatDTO,
    val beregningsGrunnlag: BeregningsgrunnlagDTO?,
    val hendelsesTidspunkt: LocalDateTime,
)