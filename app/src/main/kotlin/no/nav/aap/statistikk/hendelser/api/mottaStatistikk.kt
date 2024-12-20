package no.nav.aap.statistikk.hendelser.api

import com.papsign.ktor.openapigen.APITag
import com.papsign.ktor.openapigen.route.EndpointInfo
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.AvklaringsbehovKode
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.BehovType
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.DefinisjonDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.ÅrsakTilBehandling
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegType
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.JobbInput
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.statistikk.OppgaveHendelse
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobbUtfører
import no.nav.aap.statistikk.oppgave.Oppgavestatus
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import java.util.stream.IntStream
import kotlin.math.pow
import kotlin.math.roundToLong
import no.nav.aap.behandlingsflyt.kontrakt.sak.Status as SakStatus

private val log = LoggerFactory.getLogger("MottaStatistikk")

enum class Tags(override val description: String) : APITag {
    MottaStatistikk(
        "Dette endepunktet brukes for å motta statistikk ved stopp i behandlingen."
    ),
}

val avklaringsbehov = listOf(
    AvklaringsbehovHendelseDto(
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5001`,
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.AVKLAR_STUDENT

        ),
        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
        endringer = listOf(
            EndringDTO(
                status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                tidsstempel = LocalDateTime.now().minusMinutes(10),
                endretAv = "Kelvin"
            ), EndringDTO(
                status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                tidsstempel = LocalDateTime.now().minusMinutes(5),
                endretAv = "Z994573"
            )
        )
    ), AvklaringsbehovHendelseDto(
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5003`,
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.AVKLAR_SYKDOM
        ),
        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
        endringer = listOf(
            EndringDTO(
                status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
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
    behandlingType = TypeBehandling.Førstegangsbehandling,
    ident = "1403199012345",
    behandlingOpprettetTidspunkt = LocalDateTime.now(),
    avklaringsbehov = avklaringsbehov,
    versjon = "b21e88bca4533d3e0ee3a15f51a87cbaa11a7e9c",
    mottattTid = LocalDateTime.now().minusDays(1),
    sakStatus = SakStatus.LØPENDE,
    hendelsesTidspunkt = LocalDateTime.now(),
    behandlingStatus = Status.OPPRETTET,
    årsakTilBehandling = listOf(ÅrsakTilBehandling.SØKNAD)
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
    route("/oppgave") {
        post<Unit, String, OppgaveHendelse>(
            TagModule(listOf(Tags.MottaStatistikk)), EndpointInfo("OppgaveHendelse"),
        ) { _, dto ->
            transactionExecutor.withinTransaction { conn ->
                log.info("Got DTO: $dto")

                val encodedSaksNummer = dto.oppgaveDto.saksnummer?.let { stringToNumber(it) }
                dto.oppgaveDto.behandlingRef?.let { stringToNumber(it.toString()) }

                jobbAppender.leggTil(
                    conn,
                    JobbInput(LagreOppgaveHendelseJobbUtfører).medPayload(
                        DefaultJsonMapper.toJson(dto.tilDomene())
                    ).let {
                        if (encodedSaksNummer != null) {
                            it.forSak(encodedSaksNummer)
                        }
                        it
                    }
                )
            }

            responder.respond(
                HttpStatusCode.Accepted, "{}", pipeline
            )
        }
    }
}

fun OppgaveHendelse.tilDomene(): no.nav.aap.statistikk.oppgave.OppgaveHendelse {
    val oppgaveDto = this.oppgaveDto
    return no.nav.aap.statistikk.oppgave.OppgaveHendelse(
        hendelse = when (this.hendelse) {
            HendelseType.OPPRETTET -> no.nav.aap.statistikk.oppgave.HendelseType.OPPRETTET
            HendelseType.GJENÅPNET -> no.nav.aap.statistikk.oppgave.HendelseType.GJENÅPNET
            HendelseType.RESERVERT -> no.nav.aap.statistikk.oppgave.HendelseType.RESERVERT
            HendelseType.AVRESERVERT -> no.nav.aap.statistikk.oppgave.HendelseType.AVRESERVERT
            HendelseType.LUKKET -> no.nav.aap.statistikk.oppgave.HendelseType.LUKKET
        },
        oppgaveId = requireNotNull(oppgaveDto.id) { "Trenger oppgave-ID for å skille mellom oppgavehendelser" },
        mottattTidspunkt = LocalDateTime.now(),
        personIdent = oppgaveDto.personIdent,
        saksnummer = oppgaveDto.saksnummer,
        behandlingRef = oppgaveDto.behandlingRef,
        journalpostId = oppgaveDto.journalpostId,
        enhet = oppgaveDto.enhet,
        avklaringsbehovKode = oppgaveDto.avklaringsbehovKode,
        status = when (oppgaveDto.status) {
            no.nav.aap.oppgave.verdityper.Status.OPPRETTET -> Oppgavestatus.OPPRETTET
            no.nav.aap.oppgave.verdityper.Status.AVSLUTTET -> Oppgavestatus.AVSLUTTET
        },
        reservertAv = oppgaveDto.reservertAv,
        reservertTidspunkt = oppgaveDto.reservertTidspunkt,
        opprettetTidspunkt = oppgaveDto.opprettetTidspunkt,
        endretAv = oppgaveDto.endretAv,
        endretTidspunkt = oppgaveDto.endretTidspunkt,
    )
}

private fun stringToNumber(string: String): Long {
    return IntStream.range(0, string.length)
        .mapToObj() { 10.0.pow(it.toDouble()) * string[it].code }
        .reduce { acc, curr -> acc + curr }.orElse(0.0).mod(1_000_000.0).roundToLong()
}