package no.nav.aap.statistikk.pdl

import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import java.net.URI


interface PdlClient {
    fun hentPersoner(ident: List<String>): List<Person>
}

class PdlGraphQLClient(private val pdlConfig: PdlConfig) : PdlClient {
    private val client = RestClient(
        config = ClientConfig(),
        tokenProvider = no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider,
        responseHandler = DefaultResponseHandler()
    )

    override fun hentPersoner(ident: List<String>): List<Person> {
        val graphQLRespons = client.post<Any, GraphQLRespons<List<HentPersonBolkResult>>>(
            URI.create(pdlConfig.url), PostRequest(body = PdlRequest.hentPersonBolk(ident))
        )

        val graphQLdata = requireNotNull(graphQLRespons?.data)

        return graphQLdata.map { requireNotNull(it.person) { "Fant ikke info om person ${it.ident}" } }
    }

}

data class PdlConfig(val url: String)

internal data class PdlRequest(val query: String, val variables: Variables) {
    data class Variables(val ident: String? = null, val identer: List<String>? = null)

    companion object {
        fun hentPersonBolk(personidenter: List<String>) = PdlRequest(
            query = PERSON_BOLK_QUERY,
            variables = Variables(identer = personidenter),
        )
    }
}

data class HentPersonBolkResult(val ident: String, val person: Person?)

private const val identer = "\$identer"
val PERSON_BOLK_QUERY = """
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

data class Person(val adressebeskyttelse: Adressebeskyttelse)

data class Adressebeskyttelse(
    val gradering: Gradering
)

enum class Gradering {
    FORTROLIG, STRENGT_FORTROLIG_UTLAND, STRENGT_FORTROLIG, UGRADERT
}