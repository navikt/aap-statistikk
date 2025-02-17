package no.nav.aap.statistikk.avsluttetbehandling

import java.util.*

interface IRettighetstypeperiodeRepository {
    fun lagre(
        behandlingReferanse: UUID,
        rettighetstypePeriode: List<RettighetstypePeriode>
    )

    fun hent(behandlingReferanse: UUID): List<RettighetstypePeriode>
}