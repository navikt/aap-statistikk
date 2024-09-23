package no.nav.aap.statistikk.sak

interface SakRepository {
    fun hentSak(sakID: Long): Sak?
    fun hentSak(saksnummer: String): Sak?
    fun settInnSak(sak: Sak): Long
    fun tellSaker(): Int
}