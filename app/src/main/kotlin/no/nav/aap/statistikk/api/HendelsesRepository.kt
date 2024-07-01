package no.nav.aap.statistikk.api

interface HendelsesRepository {
    fun lagreHendelse(hendelse: MottaStatistikkDTO)
}
