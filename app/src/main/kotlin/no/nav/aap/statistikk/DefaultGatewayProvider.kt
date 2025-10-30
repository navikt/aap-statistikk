package no.nav.aap.statistikk

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.gateway.GatewayRegistry
import no.nav.aap.statistikk.integrasjoner.pdl.PdlGraphQLGateway


fun createGatewayProvider(body: GatewayRegistry.() -> Unit): GatewayProvider {
    return GatewayProvider(GatewayRegistry().apply(body))
}

/* Burde endre GatewayRegistry til å ikke være stateful. */
fun defaultGatewayProvider(utvidelser: GatewayRegistry.() -> Unit = {}) = createGatewayProvider {
    register<PdlGraphQLGateway>()
    utvidelser()
}