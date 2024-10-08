package no.nav.aap.statistikk.sak

import no.nav.aap.statistikk.api_kontrakt.SakStatus
import no.nav.aap.statistikk.person.Person
import java.time.LocalDateTime

data class Sak(
    val id: Long? = null,
    val saksnummer: String,
    val person: Person,
    val sakStatus: SakStatus,
    val sistOppdatert: LocalDateTime,
    val snapShotId: Long? = null
)