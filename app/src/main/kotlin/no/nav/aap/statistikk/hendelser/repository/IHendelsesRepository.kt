package no.nav.aap.statistikk.hendelser.repository

import no.nav.aap.statistikk.api_kontrakt.MottaStatistikkDTO

interface IHendelsesRepository {
    fun lagreHendelse(hendelse: MottaStatistikkDTO): Int

    fun hentHendelser(): Collection<MottaStatistikkDTO>

    fun tellHendelser(): Int
}
