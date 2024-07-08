package no.nav.aap.statistikk.api

interface IHendelsesRepository {
    fun lagreHendelse(hendelse: MottaStatistikkDTO)

    fun hentHendelser(): Collection<MottaStatistikkDTO>
}
