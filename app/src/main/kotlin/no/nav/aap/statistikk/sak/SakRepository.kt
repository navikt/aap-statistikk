package no.nav.aap.statistikk.sak

typealias SakId = Long

interface SakRepository {
    fun hentSak(sakID: SakId): Sak?
    fun hentSak(saksnummer: String): Sak?
    fun settInnSak(sak: Sak): SakId
    fun tellSaker(): Int
}