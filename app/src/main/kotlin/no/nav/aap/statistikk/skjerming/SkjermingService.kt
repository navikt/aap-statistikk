package no.nav.aap.statistikk.skjerming

import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.integrasjoner.pdl.Gradering
import no.nav.aap.statistikk.integrasjoner.pdl.PdlGateway
import java.net.http.HttpConnectTimeoutException

private val logger = org.slf4j.LoggerFactory.getLogger("SkjermingService")

class SkjermingService(
    private val pdlGateway: PdlGateway
) {
    fun erSkjermet(behandling: Behandling): Boolean {
        val identer = behandling.identerPåBehandling()
        return try {
            val hentPersoner = pdlGateway.hentPersoner(identer)
            hentPersoner
                .flatMap { it.adressebeskyttelse }
                .any { it.gradering.erHemmelig() }
        } catch (e: HttpConnectTimeoutException) {
            logger.error("Feilet kall til PDL (HttpConnectTimeoutException). Kaster exception.", e)
            throw e
        } catch (e: java.net.ConnectException) {
            logger.error("Feilet kall til PDL (ConnectException). Kaster exception.", e)
            throw e
        }
    }
}

fun Gradering.erHemmelig(): Boolean {
    return this != Gradering.UGRADERT
}