package no.nav.aap.statistikk.skjerming

import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.integrasjoner.pdl.Gradering
import no.nav.aap.statistikk.integrasjoner.pdl.PdlGateway

private val logger = org.slf4j.LoggerFactory.getLogger("SkjermingService")

class SkjermingService(
    private val pdlGateway: PdlGateway
) {
    fun erSkjermet(behandling: Behandling): Boolean {
        val identer = behandling.identerPåBehandling()
        try {
            val hentPersoner = pdlGateway.hentPersoner(identer)
            return hentPersoner
                .flatMap { it.adressebeskyttelse }
                .any { it.gradering.erHemmelig() }
        } catch (e: Exception) {
            logger.error("Feilet kall til PDL (${e.javaClass.simpleName}). Returnerer false for skjerming. Se stackTrace.", e)
            return false;
        }
    }
}

fun Gradering.erHemmelig(): Boolean {
    return this != Gradering.UGRADERT
}