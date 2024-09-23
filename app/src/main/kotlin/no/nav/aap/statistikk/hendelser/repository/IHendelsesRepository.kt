package no.nav.aap.statistikk.hendelser.repository

import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling

interface IHendelsesRepository {
    fun lagreHendelse(hendelse: StoppetBehandling, sakId: Long): Int

    fun hentHendelser(): Collection<StoppetBehandling>

    fun tellHendelser(): Int
}
