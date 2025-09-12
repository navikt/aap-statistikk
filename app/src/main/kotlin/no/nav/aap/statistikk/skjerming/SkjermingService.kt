package no.nav.aap.statistikk.skjerming

import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.integrasjoner.pdl.Gradering
import no.nav.aap.statistikk.integrasjoner.pdl.PdlClient

private val logger = org.slf4j.LoggerFactory.getLogger("SkjermingService")

class SkjermingService(
    private val pdlClient: PdlClient
) {
    fun erSkjermet(behandling: Behandling): Boolean {
        val identer = listOf(behandling.sak.person.ident) + behandling.relaterteIdenter
        try {
            val hentPersoner = pdlClient.hentPersoner(identer)
            return hentPersoner
                .flatMap { it.adressebeskyttelse }
                .any { it.gradering.erHemmelig() }
        } catch (e: Exception) {
            logger.warn("Feilet kall til PDL. Returnerer false for skjerming. Se stackTrace.", e)
            return false;
        }
    }
}

fun Gradering.erHemmelig(): Boolean {
    return this != Gradering.UGRADERT
}