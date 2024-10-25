package no.nav.aap.statistikk.pdl

import no.nav.aap.statistikk.behandling.Behandling

class SkjermingService(
    private val pdlClient: PdlClient
) {

    fun erSkjermet(behandling: Behandling): Boolean {
        val identer = listOf(behandling.sak.person.ident) + behandling.relaterteIdenter
        val hentPersoner = pdlClient.hentPersoner(identer)

        return hentPersoner.any { it.adressebeskyttelse.gradering.erHemmelig() }
    }
}

fun Gradering.erHemmelig(): Boolean {
    return this != Gradering.UGRADERT
}