package no.nav.aap.statistikk.server.authenticate

import java.net.URI
import java.net.URL

data class AzureConfig(
    val clientId: String,
    val jwks: URL,
    val issuer: String,
) {
    companion object {
        fun fraMiljøVariabler(): AzureConfig {
            val clientId = System.getenv("AZURE_APP_CLIENT_ID")
            val jwks = System.getenv("AZURE_OPENID_CONFIG_JWKS_URI")
            val issuer = System.getenv("AZURE_OPENID_CONFIG_ISSUER")

            return AzureConfig(clientId, URI.create(jwks).toURL(), issuer)
        }
    }
}