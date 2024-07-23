package no.nav.aap.statistikk.vilkårsresultat.api

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import io.ktor.server.application.*
import no.nav.aap.statistikk.vilkårsresultat.service.VilkårsResultatService
import java.util.*

data class VilkårsResultatResponsDTO(val id: Int)

fun NormalOpenAPIRoute.vilkårsResultat(
    vilkårsResultatService: VilkårsResultatService
) {
    route("/vilkarsresultat") {
        post<Unit, VilkårsResultatResponsDTO, VilkårsResultatDTO> { _, dto ->
            pipeline.context.application.log.info("Mottok vilkårsresultat: $dto")

            // TODO: denne skal erstattes med /avsluttetBehandling
            val behandlingsReferanse = UUID.randomUUID()
            val id = vilkårsResultatService.mottaVilkårsResultat(dto.tilDomene("sakid", behandlingsReferanse))

            responder.respond(HttpStatusCode.Accepted, VilkårsResultatResponsDTO(id=id), pipeline)
        }
    }
}