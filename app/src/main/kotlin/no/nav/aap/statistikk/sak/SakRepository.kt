package no.nav.aap.statistikk.sak

import no.nav.aap.komponenter.repository.Repository

typealias SakId = Long

interface SakRepository : Repository {
    fun hentSak(sakID: SakId): Sak
    fun hentSak(saksnummer: Saksnummer): Sak
    fun hentSakEllernull(saksnummer: Saksnummer): Sak?
    fun settInnSak(sak: Sak): SakId
    fun oppdaterSak(sak: Sak)
    fun tellSaker(): Int
}