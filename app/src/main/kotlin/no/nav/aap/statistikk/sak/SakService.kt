package no.nav.aap.statistikk.sak

import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.person.Person
import java.time.Clock
import java.time.LocalDateTime

class SakService(private val sakRepository: SakRepository, private val clock: Clock = Clock.systemDefaultZone()) {
    constructor(repositoryProvider: RepositoryProvider) : this(repositoryProvider.provide())

    fun hentEllerSettInnSak(person: Person, saksnummer: Saksnummer): Sak {
        var sak = sakRepository.hentSakEllernull(saksnummer)
        if (sak == null) {
            val sakId = sakRepository.settInnSak(
                Sak(
                    id = null,
                    saksnummer = saksnummer,
                    person = person,
                    sistOppdatert = LocalDateTime.now(clock)
                ),
            )
            sak = sakRepository.hentSak(sakId)
        }
        return sak
    }
}
