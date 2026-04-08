package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.repository.Repository
import java.util.*

interface RettighetstypeperiodeRepository : Repository {
    fun lagre(
        behandlingReferanse: UUID,
        rettighetstypePeriode: List<RettighetstypePeriode>
    )

    fun hent(behandlingReferanse: UUID): List<RettighetstypePeriode>
}