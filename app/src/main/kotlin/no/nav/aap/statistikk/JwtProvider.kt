package no.nav.aap.statistikk

import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.model.Described
import com.papsign.ktor.openapigen.model.security.HttpSecurityScheme
import com.papsign.ktor.openapigen.model.security.SecuritySchemeModel
import com.papsign.ktor.openapigen.model.security.SecuritySchemeType
import com.papsign.ktor.openapigen.modules.providers.AuthProvider
import com.papsign.ktor.openapigen.route.path.auth.OpenAPIAuthenticatedRoute
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

enum class Scopes(override val description: String) : Described {
    AAP_STATISTIKK("aap_statistikk")
}

class JwtProvider : AuthProvider<JWTPrincipal> {
    override val security: Iterable<Iterable<AuthProvider.Security<*>>> = listOf(
        listOf(
            AuthProvider.Security(
                SecuritySchemeModel(
                    SecuritySchemeType.http,
                    scheme = HttpSecurityScheme.bearer,
                    bearerFormat = "JWT",
                    referenceName = "jwtAuth",
                ), listOf(Scopes.AAP_STATISTIKK)
            )
        )
    )

    override fun apply(route: NormalOpenAPIRoute): OpenAPIAuthenticatedRoute<JWTPrincipal> {
        val authenticatedKtorRoute = route.ktorRoute.authenticate { }
        return OpenAPIAuthenticatedRoute(authenticatedKtorRoute, route.provider.child(), this)
    }

    override suspend fun getAuth(pipeline: PipelineContext<Unit, ApplicationCall>): JWTPrincipal {
        return pipeline.context.authentication.principal() ?: throw RuntimeException("No JWT Principal")
    }
}

internal fun Application.generateOpenAPI() {
    install(OpenAPIGen) {
        // this serves OpenAPI definition on /openapi.json
        serveOpenApiJson = true
        // this serves Swagger UI on /swagger-ui/index.html
        serveSwaggerUi = true
        info {
            title = "AAP - Statistikk"
        }
        addModules(JwtProvider())
    }
}


/**
 * Triks for å få NormalOpenAPIRoute til å virke med auth
 * Kopiert fra behandlingsflyt
 */
@KtorDsl
fun Route.apiRoute(config: NormalOpenAPIRoute.() -> Unit) {
    NormalOpenAPIRoute(
        this,
        application.plugin(OpenAPIGen).globalModuleProvider
    ).apply(config)
}