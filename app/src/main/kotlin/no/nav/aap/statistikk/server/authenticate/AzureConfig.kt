package no.nav.aap.statistikk.server.authenticate

import java.net.URI
import java.net.URL

private fun getEnvVar(envVar: String) = System.getenv(envVar) ?: error("missing envvar $envVar")
class AzureConfig(
    val clientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val jwks: URL = URI.create(getEnvVar("AZURE_OPENID_CONFIG_JWKS_URI")).toURL(),
    val issuer: String = getEnvVar("AZURE_OPENID_CONFIG_ISSUER")
)