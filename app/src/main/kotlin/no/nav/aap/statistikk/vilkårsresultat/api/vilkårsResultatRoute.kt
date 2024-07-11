package no.nav.aap.statistikk.vilkårsresultat.api

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import io.ktor.server.application.*
import no.nav.aap.statistikk.vilkårsresultat.service.VilkårsResultatService


fun NormalOpenAPIRoute.vilkårsResultat(
    vilkårsResultatService: VilkårsResultatService
) {
    route("/vilkarsresultat") {
        post<Unit, String, VilkårsResultatDTO> { _, dto ->
            pipeline.context.application.log.info("Mottok vilkårsresultat: $dto")

            vilkårsResultatService.mottaVilkårsResultat(dto.tilDomene())

            responder.respond(HttpStatusCode.Accepted, "{}", pipeline)
        }
    }
}