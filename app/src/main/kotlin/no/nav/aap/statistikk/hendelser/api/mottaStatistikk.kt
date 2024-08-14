package no.nav.aap.statistikk.hendelser.api

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("MottaStatistikk")

fun NormalOpenAPIRoute.mottaStatistikk(
    hendelsesRepository: IHendelsesRepository,
) {
    route("/motta") {
        post<Unit, String, MottaStatistikkDTO> { _, dto ->
            hendelsesRepository.lagreHendelse(dto)

            log.info("Got DTO: $dto")

            // Må ha String-respons på grunn av Accept-header. Denne må returnere json
            responder.respond(HttpStatusCode.Accepted, "{}", pipeline)
        }
    }
}