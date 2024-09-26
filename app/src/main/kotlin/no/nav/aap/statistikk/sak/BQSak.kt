package no.nav.aap.statistikk.sak

import no.nav.aap.statistikk.person.Person

data class Sak(val id: Long? = 0L, val saksnummer: String, val person: Person)

data class BQBehandling(
    val behandlingUUID: String,
)

data class BQSak(
    val saksnummer: String,
    val behandlinger: List<BQBehandling>
)

