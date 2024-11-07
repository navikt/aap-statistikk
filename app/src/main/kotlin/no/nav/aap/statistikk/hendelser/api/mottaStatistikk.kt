package no.nav.aap.statistikk.hendelser.api

import com.papsign.ktor.openapigen.APITag
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.JobbInput
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.*
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegType
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import java.util.stream.IntStream
import kotlin.math.pow
import kotlin.math.roundToLong

private val log = LoggerFactory.getLogger("MottaStatistikk")

enum class Tags(override val description: String) : APITag {
    MottaStatistikk(
        "Dette endepunktet brukes for å motta statistikk ved stopp i behandlingen."
    ),
}

val avklaringsbehov = listOf(
    AvklaringsbehovHendelse(
        definisjon = Definisjon(
            type = "5001",
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.AVKLAR_STUDENT

        ), status = EndringStatus.AVSLUTTET, endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.now().minusMinutes(10),
                endretAv = "Kelvin"
            ), Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.now().minusMinutes(5),
                endretAv = "Z994573"
            )
        )
    ), AvklaringsbehovHendelse(
        definisjon = Definisjon(
            type = "5003",
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.AVKLAR_SYKDOM
        ), status = EndringStatus.OPPRETTET, endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.now().minusMinutes(3),
                endretAv = "Kelvin"
            )
        )
    )
)

private val eksempelUUID = UUID.randomUUID()

val exampleRequestStoppetBehandling = StoppetBehandling(
    saksnummer = "4LFL5CW",
    behandlingReferanse = eksempelUUID,
    status = BehandlingStatus.OPPRETTET,
    behandlingType = TypeBehandling.Førstegangsbehandling,
    ident = "1403199012345",
    behandlingOpprettetTidspunkt = LocalDateTime.now(),
    avklaringsbehov = avklaringsbehov,
    versjon = "b21e88bca4533d3e0ee3a15f51a87cbaa11a7e9c",
    mottattTid = LocalDateTime.now().minusDays(1),
    sakStatus = SakStatus.LØPENDE,
    hendelsesTidspunkt = LocalDateTime.now()
)


fun NormalOpenAPIRoute.mottaStatistikk(
    transactionExecutor: TransactionExecutor,
    jobbAppender: JobbAppender,
    lagreStoppetHendelseJobb: LagreStoppetHendelseJobb,
) {
    route("/stoppetBehandling") {
        post<Unit, String, StoppetBehandling>(
            TagModule(listOf(Tags.MottaStatistikk)),
            exampleRequest = exampleRequestStoppetBehandling
        ) { _, dto ->
            transactionExecutor.withinTransaction { conn ->
                log.info("Got DTO: $dto")

                val stringified = DefaultJsonMapper.toJson(dto)

                val encodedSaksNummer = stringToNumber(dto.saksnummer)
                val encodedBehandlingsUUID = stringToNumber(dto.behandlingReferanse.toString())

                jobbAppender.leggTil(
                    conn,
                    JobbInput(lagreStoppetHendelseJobb).medPayload(stringified).medCallId()
                        .forBehandling(encodedSaksNummer, encodedBehandlingsUUID)
                )
            }

            responder.respond(
                HttpStatusCode.Accepted, "{}", pipeline
            )
        }
    }
}

private fun stringToNumber(string: String): Long {
    return IntStream.range(0, string.length)
        .mapToObj() { 10.0.pow(it.toDouble()) * string[it].code }
        .reduce { acc, curr -> acc + curr }.orElse(0.0).mod(1_000_000.0).roundToLong()
}