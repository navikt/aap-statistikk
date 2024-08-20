package no.nav.aap.statistikk.avsluttetbehandling.api

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import io.ktor.server.application.*
import no.nav.aap.statistikk.api_kontrakt.AvsluttetBehandlingDTO
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService

data class AvsluttetBehandlingResponsDTO(val id: Int)

fun NormalOpenAPIRoute.avsluttetBehandling(avsluttetBehandlingService: AvsluttetBehandlingService) {
    route("/avsluttetBehandling") {
        post<Unit, AvsluttetBehandlingResponsDTO, AvsluttetBehandlingDTO> { _, dto ->
            pipeline.context.application.log.info("Mottok avsluttet behandling: $dto")

            val id = avsluttetBehandlingService.lagre(dto.tilDomene())

            // TODO: responder med id?
            responder.respond(HttpStatusCode.Accepted, AvsluttetBehandlingResponsDTO(id = 143), pipeline)
        }
    }
}