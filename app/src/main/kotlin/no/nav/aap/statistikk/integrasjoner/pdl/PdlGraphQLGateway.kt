package no.nav.aap.statistikk.integrasjoner.pdl

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.gateway.Factory
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureM2MTokenProvider
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.WithMetrics
import no.nav.aap.statistikk.person.Ident
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

private const val BEHANDLINGSNUMMER_AAP_SAKSBEHANDLING = "B287"

class PdlGraphQLGateway : PdlGateway {
    private val logger = LoggerFactory.getLogger(PdlGateway::class.java)

    companion object : Factory<PdlGateway>, WithMetrics {
        private val pdlCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(4))
            .recordStats()
            .build<String, List<PdlPerson>>()

        override fun konstruer(): PdlGateway {
            return PdlGraphQLGateway()
        }

        override fun registrerMetrics(registry: MeterRegistry) {
            CaffeineCacheMetrics.monitor(registry, pdlCache, "pdl")
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
        tokenProvider = AzureM2MTokenProvider,
        responseHandler = DefaultResponseHandler(),
        prometheus = PrometheusProvider.prometheus
    )

    override fun hentPersoner(identer: List<Ident>): List<PdlPerson> {
        logger.debug("Henter ${identer.size} personer fra PDL.")

        val cacheNøkkel = sha256(identer.map { it.ident }.sorted().joinToString(","))
        return pdlCache.get(cacheNøkkel) {
            val graphQLRespons = client.post<Any, GraphQLRespons<PdlRespons>>(
                URI.create(requiredConfigForKey("integrasjon.pdl.url")),
                PostRequest(body = PdlRequest.hentPersonBolk(identer))
            )

            val graphQLdata =
                requireNotNull(graphQLRespons?.data) { "Ingen data på graphql-respons. Errors: ${graphQLRespons?.errors}" }

            graphQLdata.hentPersonBolk.map { personBolk -> requireNotNull(personBolk.person) { "Fant ikke info om person (ident maskert)" } }
        }
    }

    override fun hentIdenter(ident: Ident): List<PdlIdent> {
        val graphQLRespons = client.post<Any, GraphQLRespons<PdlIdenterRespons>>(
            URI.create(requiredConfigForKey("integrasjon.pdl.url")),
            PostRequest(body = PdlRequest.hentIdenter(ident))
        )

        val graphQLData =
            requireNotNull(graphQLRespons?.data) { "Ingen data på graphql-respons. Errors: ${graphQLRespons?.errors}" }

        return requireNotNull(graphQLData.hentIdenter) { "Fant ingen identer i PDL." }
            .identer
            .filter { it.gruppe == "FOLKEREGISTERIDENT" }
    }
}

private fun sha256(input: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.toByteArray()))