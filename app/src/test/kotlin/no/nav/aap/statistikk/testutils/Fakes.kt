package no.nav.aap.statistikk.testutils

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.statistikk.pdl.*
import org.junit.jupiter.api.extension.*
import java.net.URI

@Target(AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER)
@ExtendWith(Fakes.FakesExtension::class)
annotation class Fakes {
    class AzureFake(port: Int = 0) {
        private val azure = embeddedServer(Netty, port = port, module = { azureFake() })

        fun start() {
            azure.start()
        }

        fun close() {
            azure.stop(500L, 10_000L)
        }

        fun port(): Int = azure.port()

        private fun Application.azureFake() {
            install(ContentNegotiation) {
                jackson()
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    this@azureFake.log.info(
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
                post("/token") {
                    val token = AzureTokenGen("tilgang", "tilgang").generate()
                    call.respond(TestToken(access_token = token, scope = "AAP_SCOPES"))
                }
                get("/jwks") {
                    call.respond(AZURE_JWKS)
                }
            }
        }
    }

    class PdlFake(port: Int = 0) {
        private val pdl = embeddedServer(Netty, port = port, module = { pdlFake() })

        fun start() {
            pdl.start()
        }

        fun close() {
            pdl.stop(500L, 10_000L)
        }

        fun port(): Int = pdl.port()

        private fun Application.pdlFake() {
            install(ContentNegotiation) {
                jackson()
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    this@pdlFake.log.info(
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

    class FakesExtension : AfterAllCallback, BeforeAllCallback, ParameterResolver {
        private val azure = AzureFake()
        private val pdl = PdlFake()

        override fun beforeAll(context: ExtensionContext?) {
            azure.start()
            pdl.start()
        }

        override fun afterAll(context: ExtensionContext?) {
            azure.close()
            pdl.close()
        }

        override fun supportsParameter(
            parameterContext: ParameterContext?,
            extensionContext: ExtensionContext?
        ): Boolean {
            return (parameterContext?.isAnnotated(Fakes::class.java) == true
                    && (parameterContext.parameter.type == TestToken::class.java))
                    || parameterContext?.isAnnotated(
                Fakes::class.java
            ) == true
                    && (parameterContext.parameter.type == AzureConfig::class.java) || (parameterContext?.isAnnotated(
                Fakes::class.java
            ) == true
                    && (parameterContext.parameter.type == PdlConfig::class.java))
        }

        override fun resolveParameter(
            parameterContext: ParameterContext?,
            extensionContext: ExtensionContext?
        ): Any {
            if (parameterContext?.isAnnotated(Fakes::class.java) == true
                && parameterContext.parameter.type == AzureConfig::class.java
            ) {
                return AzureConfig(
                    clientId = "tilgang",
                    jwksUri = "http://localhost:${azure.port()}/jwks",
                    issuer = "tilgang",
                    tokenEndpoint = URI.create("http://localhost:${azure.port()}/token"),
                    clientSecret = "verysecret",
                )
            } else if (parameterContext?.isAnnotated(Fakes::class.java) == true
                && parameterContext.parameter.type == TestToken::class.java
            ) {
                val token = AzureTokenGen("tilgang", "tilgang").generate()
                return TestToken(access_token = token, scope = "AAP_SCOPES")
            } else if (parameterContext?.isAnnotated(Fakes::class.java) == true
                && parameterContext.parameter.type == PdlConfig::class.java
            ) {
                return PdlConfig(
                    url = "http://localhost:${pdl.port()}",
                    scope = "..."
                )
            } else {
                throw IllegalArgumentException("Ukjent parametertype")
            }
        }
    }

    data class ErrorRespons(val message: String?)

    @Suppress("PropertyName")
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