package no.nav.aap.statistikk.sak

import no.nav.aap.statistikk.person.Person
import java.time.Clock
import java.time.LocalDateTime

class SakService(
    private val sakRepository: SakRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
     fun hentEllerSettInnSak(
        person: Person,
        saksnummer: Saksnummer,
        sakStatus: SakStatus
    ): Sak {
        var sak = sakRepository.hentSakEllernull(saksnummer)
        if (sak == null) {
            val sakId = sakRepository.settInnSak(
                Sak(
                    id = null,
                    saksnummer = saksnummer,
                    person = person,
                    sistOppdatert = LocalDateTime.now(clock),
                    sakStatus = sakStatus
                )
            )
            sak = sakRepository.hentSak(sakId)
        } else {
            sakRepository.oppdaterSak(
                sak.copy(
                    sakStatus = sakStatus,
                    sistOppdatert = LocalDateTime.now(clock)
                )
            )
        }
        return sak
    }
}