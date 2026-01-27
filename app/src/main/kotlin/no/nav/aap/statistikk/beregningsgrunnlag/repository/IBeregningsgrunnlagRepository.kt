package no.nav.aap.statistikk.beregningsgrunnlag.repository

import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import java.util.UUID

interface IBeregningsgrunnlagRepository : Repository {
    fun lagreBeregningsGrunnlag(beregningsGrunnlag: MedBehandlingsreferanse<IBeregningsGrunnlag>): Long
    fun hentBeregningsGrunnlag(referanse: UUID): List<MedBehandlingsreferanse<IBeregningsGrunnlag>>
}