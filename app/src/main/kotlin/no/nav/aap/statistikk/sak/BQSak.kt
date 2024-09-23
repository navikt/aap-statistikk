package no.nav.aap.statistikk.sak

import no.nav.aap.statistikk.person.Person

data class Sak(val id: Long?, val saksnummer: String, val person: Person)

data class BQSak(
    val saksnummer: String,
    val behandlinger: List<Behandling>
)

data class Behandling(val referanse: String)