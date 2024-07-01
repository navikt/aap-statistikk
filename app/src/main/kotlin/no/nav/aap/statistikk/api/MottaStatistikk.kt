package no.nav.aap.statistikk.api

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("MottaStatistikk")

fun NormalOpenAPIRoute.mottaStatistikk(hendelsesRepository: HendelsesRepository) {
    route("/motta") {
        post<Unit, String, MottaStatistikkDTO> { _, dto ->
            hendelsesRepository.lagreHendelse(dto)

            log.info("Got dto.")

            // Må ha String-respons på grunn av Accept-header. Denne må returnere json
            responder.respond(HttpStatusCode.Accepted, "{}", pipeline)
        }
    }
}