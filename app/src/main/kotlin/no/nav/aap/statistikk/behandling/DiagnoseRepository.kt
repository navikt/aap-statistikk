package no.nav.aap.statistikk.behandling

import java.util.*

interface DiagnoseRepository {
    fun lagre(diagnoseEntity: DiagnoseEntity): Long
    fun hentForBehandling(behandlingReferanse: UUID): DiagnoseEntity
}