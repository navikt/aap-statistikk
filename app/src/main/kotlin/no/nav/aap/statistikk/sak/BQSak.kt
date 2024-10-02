package no.nav.aap.statistikk.sak

import no.nav.aap.statistikk.person.Person
import java.time.LocalDateTime

data class Sak(val id: Long? = 0L, val saksnummer: String, val person: Person)

data class BQBehandling(
    val behandlingUUID: String,
    val behandlingType: String,
    val saksnummer: String,
    val tekniskTid: LocalDateTime,
    val verson: String,
    val avsender: String,
) {
    init {
        require(behandlingType.uppercase() == behandlingType)
    }
}