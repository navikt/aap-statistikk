package no.nav.aap.statistikk.integrasjoner.pdl

import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import org.slf4j.LoggerFactory
import java.net.URI

private val logger = LoggerFactory.getLogger(PdlClient::class.java)

interface PdlClient {
    fun hentPersoner(identer: List<String>): List<Person>
}

private const val BEHANDLINGSNUMMER_AAP_SAKSBEHANDLING = "B287"

class PdlGraphQLClient(private val pdlConfig: PdlConfig, meterRegistry: MeterRegistry) : PdlClient {
    private val client = RestClient(
        config = ClientConfig(
            scope = pdlConfig.scope,
            additionalHeaders = listOf(
                Header(
                    "behandlingsnummer",
                    BEHANDLINGSNUMMER_AAP_SAKSBEHANDLING
                )
            )
        ),
        tokenProvider = ClientCredentialsTokenProvider,
        responseHandler = DefaultResponseHandler(),
        prometheus = meterRegistry
    )

    override fun hentPersoner(identer: List<String>): List<Person> {
        logger.info("Henter ${identer.size} personer fra PDL.")

        val graphQLRespons = client.post<Any, GraphQLRespons<PdlRespons>>(
            URI.create(pdlConfig.url), PostRequest(body = PdlRequest.hentPersonBolk(identer))
        )

        val graphQLdata =
            requireNotNull(graphQLRespons?.data) { "Ingen data på graphql-respons. Errors: ${graphQLRespons?.errors}" }

        return graphQLdata.hentPersonBolk.map { requireNotNull(it.person) { "Fant ikke info om person ${it.ident}" } }
    }

}

data class PdlConfig(val url: String, val scope: String)

internal data class PdlRequest(val query: String, val variables: Variables) {
    data class Variables(val ident: String? = null, val identer: List<String>? = null)

    companion object {
        fun hentPersonBolk(personidenter: List<String>) = PdlRequest(
            query = PERSON_BOLK_QUERY,
            variables = Variables(identer = personidenter),
        )
    }
}

data class PdlRespons(val hentPersonBolk: List<HentPersonBolkResult>)

data class HentPersonBolkResult(val ident: String, val person: Person?)

val PERSON_BOLK_QUERY = $$"""
    query($identer: [ID!]!) {
        hentPersonBolk(identer: $identer) {
            ident,
            person {
                adressebeskyttelse {
                    gradering
                },
            }
            code
        }
    }
""".trimIndent()

data class Person(val adressebeskyttelse: List<Adressebeskyttelse>)

data class Adressebeskyttelse(
    val gradering: Gradering
)

enum class Gradering {
    FORTROLIG, STRENGT_FORTROLIG_UTLAND, STRENGT_FORTROLIG, UGRADERT
}