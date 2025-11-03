package no.nav.aap.statistikk.sak

import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.statistikk.behandling.Behandling

interface IBigQueryKvitteringRepository : Repository {
    fun lagreKvitteringForSak(behandling: Behandling): Long
}