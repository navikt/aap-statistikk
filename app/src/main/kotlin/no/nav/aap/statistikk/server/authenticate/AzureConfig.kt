package no.nav.aap.statistikk.server.authenticate

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import java.net.URI


fun azureconfigFraMiljøVariabler(): AzureConfig {
    val clientId = System.getenv("AZURE_APP_CLIENT_ID")
    val jwks = System.getenv("AZURE_OPENID_CONFIG_JWKS_URI")
    val issuer = System.getenv("AZURE_OPENID_CONFIG_ISSUER")
    val tokenEndpoint = System.getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")
    val clientSecret = System.getenv("AZURE_APP_CLIENT_SECRET")

    return AzureConfig(
        tokenEndpoint = URI.create(tokenEndpoint),
        clientId = clientId,
        clientSecret = clientSecret,
        jwksUri = jwks,
        issuer = issuer
    )
}