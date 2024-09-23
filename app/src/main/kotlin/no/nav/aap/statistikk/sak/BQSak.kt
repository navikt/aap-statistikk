package no.nav.aap.statistikk.sak

data class Person(val ident: String, val id: Long? = null)

data class Sak(val id: Long?, val saksnummer: String, val person: Person)

data class BQSak(
    val saksnummer: String,
    val behandlinger: List<Behandling>
)

data class Behandling(val referanse: String)