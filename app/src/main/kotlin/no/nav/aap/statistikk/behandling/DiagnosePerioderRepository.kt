package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.statistikk.avsluttetbehandling.DiagnoseMedPeriode
import java.util.*

interface DiagnosePerioderRepository : Repository {
    fun lagre(behandlingId: BehandlingId, diagnoser: List<DiagnoseMedPeriode>)
    fun hentForBehandling(behandlingReferanse: UUID): List<DiagnoseMedPeriode>
}
