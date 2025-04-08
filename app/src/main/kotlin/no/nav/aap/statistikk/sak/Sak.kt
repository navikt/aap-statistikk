package no.nav.aap.statistikk.sak

import no.nav.aap.statistikk.person.Person
import java.time.LocalDateTime

@JvmInline
value class Saksnummer(val value: String)

fun String.tilSaksnummer() = Saksnummer(this)

data class Sak(
    val id: Long? = null,
    val saksnummer: Saksnummer,
    val person: Person,
    val sakStatus: SakStatus,
    val sistOppdatert: LocalDateTime,
    val snapShotId: Long? = null
)

enum class SakStatus {
    OPPRETTET,
    UTREDES,
    LØPENDE,
    AVSLUTTET
}