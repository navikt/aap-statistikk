package no.nav.aap.statistikk

import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*


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