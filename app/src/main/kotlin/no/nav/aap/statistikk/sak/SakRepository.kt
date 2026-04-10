package no.nav.aap.statistikk.sak

import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.statistikk.person.Person
import java.time.Clock
import java.time.LocalDateTime

typealias SakId = Long

interface SakRepository : Repository {
    fun hentSak(sakID: SakId): Sak
    fun hentSak(saksnummer: Saksnummer): Sak
    fun hentSakEllernull(saksnummer: Saksnummer): Sak?
    fun settInnSak(sak: Sak): SakId
    fun oppdaterSak(sak: Sak)
    fun tellSaker(): Int

    fun hentEllerSettInn(
        person: Person,
        saksnummer: Saksnummer,
        sakStatus: SakStatus,
        clock: Clock = Clock.systemDefaultZone(),
    ): Sak {
        var sak = hentSakEllernull(saksnummer)
        if (sak == null) {
            val sakId = settInnSak(
                Sak(
                    id = null,
                    saksnummer = saksnummer,
                    person = person,
                    sistOppdatert = LocalDateTime.now(clock),
                    sakStatus = sakStatus
                )
            )
            sak = hentSak(sakId)
        } else {
            oppdaterSak(
                sak.copy(
                    sakStatus = sakStatus,
                    sistOppdatert = LocalDateTime.now(clock)
                )
            )
        }
        return sak
    }
}