package no.nav.aap.statistikk.sak

data class Sak(
    val saksnummer: String,
    val behandlinger: List<Behandling>
)

data class Behandling(val referanse: String)