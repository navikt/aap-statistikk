package no.nav.aap.statistikk.beregningsgrunnlag

import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(BeregningsGrunnlagService::class.java)

class BeregningsGrunnlagService(private val beregningsGrunnlagRepository: BeregningsgrunnlagRepository) {
    fun mottaBeregningsGrunnlag(beregningsgrunnlag: IBeregningsGrunnlag) {
        logger.info("Lagrer beregningsgrunnlag.")
        beregningsGrunnlagRepository.lagreBeregningsGrunnlag(beregningsgrunnlag)
    }
}