package no.nav.aap.statistikk.testutils

import AzureTokenGen
import com.fasterxml.jackson.databind.JsonNode
import com.nimbusds.jwt.JWTParser
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import no.nav.aap.statistikk.integrasjoner.pdl.Adressebeskyttelse
import no.nav.aap.statistikk.integrasjoner.pdl.Gradering
import no.nav.aap.statistikk.integrasjoner.pdl.GraphQLRespons
import no.nav.aap.statistikk.integrasjoner.pdl.HentPersonBolkResult
import no.nav.aap.statistikk.integrasjoner.pdl.PdlRespons
import no.nav.aap.statistikk.integrasjoner.pdl.Person
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.util.RestoreSystemProperties
import java.util.*

@Target(AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER)
@ExtendWith(Fakes.FakesExtension::class)
annotation class Fakes {
    /** Fast UUID brukt som azp-claim i test-tokens, og som verdi for alle integrasjon.*.azp-properties. */
    companion object {
        val TEST_AZP_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    class TexasFake(port: Int = 0) {
        private val texas = embeddedServer(Netty, port = port, module = module())

        fun start() {
            texas.start()
        }

        fun port(): Int {
            return texas.port()
        }

        private fun module(): Application.() -> Unit = {
            install(ContentNegotiation) {
                jackson()
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->

                    call.respond(
                        status = HttpStatusCode.InternalServerError,
                        message = ErrorRespons(cause.message)
                    )
                }
            }
            routing {
                post("/token") {
                    val token = AzureTokenGen("statistikk")
                        .generate(isApp = true, azp = TEST_AZP_UUID.toString())
                    call.respond(TestToken(access_token = token))
                }

                post("/token/exchange") {
                    val body = call.receive<JsonNode>()
                    val NAVident = JWTParser.parse(body["user_token"].asText())
                        .jwtClaimsSet
                        .getClaimAsString("NAVident")

                    val token = AzureTokenGen(body["target"].asText())
                        .generate(isApp = false, azp = "statistikk", navIdent = NAVident)
                    call.respond(TestToken(access_token = token))
                }

                post("/introspect") {
                    call.respond(mapOf("active" to true))
                }
            }
        }

        fun close() {
            texas.stop(500L, 10_000L)
        }
    }

    class PdlFake(port: Int = 0) {
        private val pdl = embeddedServer(Netty, port = port, module = { pdl() })

        fun start() {
            pdl.start()
        }

        fun close() {
            pdl.stop(500L, 10_000L)
        }

        fun port(): Int {
            return pdl.port()
        }

        private fun Application.pdl() {
            install(ContentNegotiation) {
                jackson()
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    this@pdl.log.info(
                        "AZURE :: Ukjent feil ved kall til '{}'",
                        call.request.local.uri,
                        cause
                    )
                    call.respond(
                        status = HttpStatusCode.InternalServerError,
                        message = ErrorRespons(cause.message)
                    )
                }
            }
            routing {
                post {
                    call.respond(
                        GraphQLRespons(
                            data = PdlRespons(
                                hentPersonBolk = listOf(
                                    HentPersonBolkResult(
                                        ident = "123",
                                        person = Person(
                                            adressebeskyttelse = listOf(
                                                Adressebeskyttelse(
                                                    gradering = Gradering.UGRADERT
                                                )
                                            )
                                        )
                                    )
                                )
                            ),
                        )
                    )
                }
            }
        }
    }

    @RestoreSystemProperties
    class FakesExtension : AfterAllCallback, BeforeAllCallback, ParameterResolver {
        private val texas = TexasFake()
        private val pdl = PdlFake()

        override fun beforeAll(context: ExtensionContext) {
            pdl.start()
            texas.start()
            System.setProperty("NAIS_TOKEN_ENDPOINT", "http://localhost:${texas.port()}/token")
            System.setProperty(
                "NAIS_TOKEN_EXCHANGE_ENDPOINT",
                "http://localhost:${texas.port()}/token/exchange"
            )
            System.setProperty(
                "NAIS_TOKEN_INTROSPECTION_ENDPOINT",
                "http://localhost:${texas.port()}/introspect"
            )
            System.setProperty("integrasjon.pdl.url", "http://localhost:${pdl.port()}")
            System.setProperty("integrasjon.pdl.scope", "xxx")

            val randomUUID = TEST_AZP_UUID
            System.setProperty("integrasjon.postmottak.azp", randomUUID.toString())
            System.setProperty("integrasjon.oppgave.azp", randomUUID.toString())
            System.setProperty("integrasjon.behandlingsflyt.azp", randomUUID.toString())
        }

        override fun afterAll(context: ExtensionContext) {
            pdl.close()
            texas.close()
        }

        override fun supportsParameter(
            parameterContext: ParameterContext,
            extensionContext: ExtensionContext
        ): Boolean {
            return (parameterContext.isAnnotated(Fakes::class.java) && (parameterContext.parameter.type == TestToken::class.java))
        }

        override fun resolveParameter(
            parameterContext: ParameterContext,
            extensionContext: ExtensionContext
        ): Any {
            if (parameterContext.isAnnotated(Fakes::class.java) && parameterContext.parameter.type == TestToken::class.java
            ) {
                val token = AzureTokenGen("tilgang").generate(isApp = true, azp = "tilgang")
                return TestToken(access_token = token, scope = "AAP_SCOPES")
            } else {
                error("Ukjent parametertype: ${parameterContext.parameter.type}")
            }
        }
    }

    data class ErrorRespons(val message: String?)

    @Suppress("PropertyName", "ConstructorParameterNaming")
    data class TestToken(
        val access_token: String,
        val refresh_token: String = "very.secure.token",
        val id_token: String = "very.secure.token",
        val token_type: String = "token-type",
        val scope: String? = null,
        val expires_in: Int = 3599,
    )

}

private fun EmbeddedServer<*, *>.port(): Int =
    runBlocking { this@port.engine.resolvedConnectors() }
        .first { it.type == ConnectorType.HTTP }
        .port