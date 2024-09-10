package no.nav.aap.statistikk.hendelser.api

import com.papsign.ktor.openapigen.APITag
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.LagreHendelseJobb
import no.nav.aap.statistikk.TransactionExecutor
import no.nav.aap.statistikk.api_kontrakt.*
import no.nav.aap.statistikk.avsluttetbehandling.api.eksempelUUID
import no.nav.aap.statistikk.hendelser.JobbAppender
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

private val log = LoggerFactory.getLogger("MottaStatistikk")

enum class Tags(override val description: String) : APITag {
    MottaStatistikk(
        "Dette endepunktet brukes for å motta statistikk ved stopp i behandlingen."
    ),
    AvsluttetBehandling(
        "Ved avsluttet behandling sendes samlet statistikk."
    )
}

fun NormalOpenAPIRoute.mottaStatistikk(
    transactionExecutor: TransactionExecutor,
    jobbAppender: JobbAppender,
) {
    val exampleRequest = MottaStatistikkDTO(
        saksnummer = "4LFL5CW",
        behandlingReferanse = eksempelUUID,
        status = "OPPRETTET",
        behandlingType = TypeBehandling.Førstegangsbehandling,
        ident = "1403199012345",
        behandlingOpprettetTidspunkt = LocalDateTime.now(),
        avklaringsbehov = listOf(
            AvklaringsbehovHendelse(
                definisjon = Definisjon(
                    type = "5001",
                    behovType = BehovType.MANUELT_PÅKREVD,
                    løsesISteg = "AVKLAR_STUDENT"

                ),
                status = EndringStatus.AVSLUTTET,
                endringer = listOf(
                    Endring(
                        status = EndringStatus.OPPRETTET,
                        tidsstempel = LocalDateTime.now().minusMinutes(10),
                        endretAv = "Kelvin"
                    ),
                    Endring(
                        status = EndringStatus.AVSLUTTET,
                        tidsstempel = LocalDateTime.now().minusMinutes(5),
                        endretAv = "Z994573"
                    )
                )
            ),
            AvklaringsbehovHendelse(
                definisjon = Definisjon(
                    type = "5003",
                    behovType = BehovType.MANUELT_PÅKREVD,
                    løsesISteg = "AVKLAR_SYKDOM"
                ),
                status = EndringStatus.OPPRETTET,
                endringer = listOf(
                    Endring(
                        status = EndringStatus.OPPRETTET,
                        tidsstempel = LocalDateTime.now().minusMinutes(3),
                        endretAv = "Kelvin"
                    )
                )
            )
        )
    )
    route("/motta") {
        post<Unit, String, MottaStatistikkDTO>(
            TagModule(listOf(Tags.MottaStatistikk)), exampleRequest = exampleRequest
        ) { _, dto ->
            transactionExecutor.withinTransaction { conn ->
                log.info("Got DTO: $dto")

                val stringified = DefaultJsonMapper.toJson(dto)

                jobbAppender.leggTil(conn, JobbInput(LagreHendelseJobb).medPayload(stringified))
            }
            // Må ha String-respons på grunn av Accept-header. Denne må returnere json
            responder.respond(HttpStatusCode.Accepted, "{}", pipeline)
        }
    }
}