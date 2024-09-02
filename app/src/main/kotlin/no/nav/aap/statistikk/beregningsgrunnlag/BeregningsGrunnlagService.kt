package no.nav.aap.statistikk.beregningsgrunnlag

import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(BeregningsGrunnlagService::class.java)

class BeregningsGrunnlagService(private val beregningsGrunnlagRepository: IBeregningsgrunnlagRepository) {
    fun mottaBeregningsGrunnlag(beregningsgrunnlag: MedBehandlingsreferanse<IBeregningsGrunnlag>) {
        logger.info("Lagrer beregningsgrunnlag.")
        beregningsGrunnlagRepository.lagreBeregningsGrunnlag(beregningsgrunnlag)
    }
}