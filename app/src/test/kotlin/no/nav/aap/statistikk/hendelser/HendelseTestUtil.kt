package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.json.DefaultJsonMapper

private fun fromResources(filnavn: String): String {
    return object {}.javaClass.getResource("/$filnavn")?.readText()!!
}

fun hendelseFraFil(filnavn: String): StoppetBehandling {
    val hendelserString = fromResources(filnavn)
    val stoppetBehandling =
        DefaultJsonMapper.fromJson<StoppetBehandling>(hendelserString)
    return stoppetBehandling
}