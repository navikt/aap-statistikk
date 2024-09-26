package no.nav.aap.statistikk.hendelser.repository

import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.sak.SakId

interface IHendelsesRepository {
    fun lagreHendelse(hendelse: StoppetBehandling, sakId: SakId, behandlingId: BehandlingId): Int

    fun hentHendelser(): Collection<StoppetBehandling>

    fun tellHendelser(): Int
}
