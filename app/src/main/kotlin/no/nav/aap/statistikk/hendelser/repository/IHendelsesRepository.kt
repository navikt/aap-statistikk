package no.nav.aap.statistikk.hendelser.repository

import no.nav.aap.statistikk.hendelser.api.MottaStatistikkDTO

interface IHendelsesRepository {
    fun lagreHendelse(hendelse: MottaStatistikkDTO): Int

    fun hentHendelser(): Collection<MottaStatistikkDTO>
}
