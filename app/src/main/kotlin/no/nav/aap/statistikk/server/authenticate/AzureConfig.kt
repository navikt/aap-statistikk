package no.nav.aap.statistikk.server.authenticate

import java.net.URI
import java.net.URL

data class AzureConfig(
    val clientId: String = requiredConfigForKey("AZURE_APP_CLIENT_ID"),
    val jwks: URL = URI.create(requiredConfigForKey("AZURE_OPENID_CONFIG_JWKS_URI")).toURL(),
    val issuer: String = requiredConfigForKey("AZURE_OPENID_CONFIG_ISSUER")
)