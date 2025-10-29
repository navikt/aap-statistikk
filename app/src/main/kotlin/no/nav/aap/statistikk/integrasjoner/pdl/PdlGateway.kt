package no.nav.aap.statistikk.integrasjoner.pdl

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.gateway.Factory
import no.nav.aap.komponenter.gateway.Gateway
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.statistikk.PrometheusProvider
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration

private val logger = LoggerFactory.getLogger(PdlGateway::class.java)

interface PdlGateway : Gateway {
    fun hentPersoner(identer: List<String>): List<Person>
}

private const val BEHANDLINGSNUMMER_AAP_SAKSBEHANDLING = "B287"

class PdlGraphQLGateway :
    PdlGateway {
    companion object : Factory<PdlGateway> {
        private val pdlCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(4))
            .recordStats()
            .build<String, List<Person>>()

        override fun konstruer(): PdlGateway {
            return PdlGraphQLGateway()
        }

        init {
            CaffeineCacheMetrics.monitor(PrometheusProvider.prometheus, pdlCache, "pdl")
        }
    }

    private val client = RestClient(
        config = ClientConfig(
            scope = requiredConfigForKey("integrasjon.pdl.scope"),
            additionalHeaders = listOf(
                Header(
                    "behandlingsnummer",
                    BEHANDLINGSNUMMER_AAP_SAKSBEHANDLING
                )
            )
        ),
        tokenProvider = ClientCredentialsTokenProvider,
        responseHandler = DefaultResponseHandler(),
        prometheus = PrometheusProvider.prometheus
    )

    override fun hentPersoner(identer: List<String>): List<Person> {
        logger.info("Henter ${identer.size} personer fra PDL.")

        return pdlCache.get(identer.joinToString(",")) {
            val graphQLRespons = client.post<Any, GraphQLRespons<PdlRespons>>(
                URI.create(requiredConfigForKey("integrasjon.pdl.url")),
                PostRequest(body = PdlRequest.hentPersonBolk(identer))
            )

            val graphQLdata =
                requireNotNull(graphQLRespons?.data) { "Ingen data på graphql-respons. Errors: ${graphQLRespons?.errors}" }

            graphQLdata.hentPersonBolk.map { requireNotNull(it.person) { "Fant ikke info om person ${it.ident}" } }
        }
    }
}

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