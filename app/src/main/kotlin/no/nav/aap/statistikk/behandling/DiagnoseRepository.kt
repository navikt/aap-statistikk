package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.repository.Repository
import java.util.*

interface DiagnoseRepository : Repository {
    fun lagre(diagnoseEntity: DiagnoseEntity): Long
    fun hentForBehandling(behandlingReferanse: UUID): DiagnoseEntity?
}