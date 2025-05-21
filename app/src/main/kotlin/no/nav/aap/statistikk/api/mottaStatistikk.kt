package no.nav.aap.statistikk.api

import com.papsign.ktor.openapigen.route.EndpointInfo
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.route.status
import io.ktor.http.*
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.AVKLAR_STUDENT
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.AVKLAR_SYKDOM
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.ÅrsakTilBehandling
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.JobbInput
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.statistikk.OppgaveHendelse
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobb
import no.nav.aap.statistikk.oppgave.Oppgavestatus
import no.nav.aap.statistikk.postmottak.LagrePostmottakHendelseJobb
import no.nav.aap.tilgang.AuthorizationMachineToMachineConfig
import no.nav.aap.tilgang.authorizedPost
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import java.util.stream.IntStream
import kotlin.math.pow
import kotlin.math.roundToLong
import no.nav.aap.behandlingsflyt.kontrakt.sak.Status as SakStatus

private val log = LoggerFactory.getLogger("MottaStatistikk")

val avklaringsbehov = listOf(
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_STUDENT,
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
        avklaringsbehovDefinisjon = AVKLAR_SYKDOM,
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

enum class Azp(val uuid: UUID) {
    Postmottak(UUID.fromString(requiredConfigForKey("integrasjon.postmottak.azp"))),
    Behandlingsflyt(UUID.fromString(requiredConfigForKey("integrasjon.behandlingsflyt.azp"))),
    Oppgave(UUID.fromString(requiredConfigForKey("integrasjon.oppgave.azp")))
}


fun NormalOpenAPIRoute.mottaStatistikk(
    transactionExecutor: TransactionExecutor,
    jobbAppender: JobbAppender,
    lagreStoppetHendelseJobb: LagreStoppetHendelseJobb,
    lagreOppgaveHendelseJobb: LagreOppgaveHendelseJobb,
    lagrePostmottakHendelseJobb: LagrePostmottakHendelseJobb,
) {
    route("/stoppetBehandling").status(HttpStatusCode.Accepted) {
        authorizedPost<Unit, String, StoppetBehandling>(
            modules = arrayOf(TagModule(listOf(Tags.MottaStatistikk))),
            routeConfig = AuthorizationMachineToMachineConfig(
                authorizedAzps = listOf(Azp.Behandlingsflyt.uuid)
            )
        ) { _, dto ->
            transactionExecutor.withinTransaction { conn ->
                val stringified = DefaultJsonMapper.toJson(dto)

                val encodedSaksNummer = stringToNumber(dto.saksnummer)

                jobbAppender.leggTil(
                    conn,
                    JobbInput(lagreStoppetHendelseJobb)
                        .medPayload(stringified)
                        .medCallId()
                        .forSak(encodedSaksNummer)
                )
            }

            responder.respond(
                HttpStatusCode.Accepted, "{}", pipeline
            )
        }
    }
    route("/oppgave").status(HttpStatusCode.Accepted) {
        post<Unit, String, OppgaveHendelse>(
            TagModule(listOf(Tags.MottaStatistikk)), EndpointInfo("OppgaveHendelse"),
        ) { _, dto ->
            transactionExecutor.withinTransaction { conn ->
                val encodedSaksNummer = dto.oppgaveDto.saksnummer?.let { stringToNumber(it) }

                jobbAppender.leggTil(
                    conn,
                    JobbInput(lagreOppgaveHendelseJobb).medPayload(
                        DefaultJsonMapper.toJson(dto.tilDomene())
                    ).let {
                        if (encodedSaksNummer != null) {
                            it.forSak(encodedSaksNummer)
                        }
                        it
                    }.medCallId()
                )
            }

            responder.respond(
                HttpStatusCode.Accepted, "{}", pipeline
            )
        }
    }
    route("/postmottak").status(HttpStatusCode.Accepted) {
        post<Unit, String, DokumentflytStoppetHendelse>(
            TagModule(listOf(Tags.MottaStatistikk)), EndpointInfo("DokumentflytStoppetHendelse"),
        ) { _, dto ->
            transactionExecutor.withinTransaction { conn ->
                jobbAppender.leggTil(
                    conn,
                    JobbInput(lagrePostmottakHendelseJobb).medPayload(
                        DefaultJsonMapper.toJson(dto)
                    ).medCallId().forSak(dto.journalpostId.referanse)
                )
            }

            responder.respond(HttpStatusCode.Accepted, "{}", pipeline)
        }
    }
}

private fun OppgaveHendelse.tilDomene(): no.nav.aap.statistikk.oppgave.OppgaveHendelse {
    val oppgaveDto = this.oppgaveDto
    return no.nav.aap.statistikk.oppgave.OppgaveHendelse(
        hendelse = when (this.hendelse) {
            HendelseType.OPPRETTET -> no.nav.aap.statistikk.oppgave.HendelseType.OPPRETTET
            HendelseType.GJENÅPNET -> no.nav.aap.statistikk.oppgave.HendelseType.OPPDATERT
            HendelseType.RESERVERT -> no.nav.aap.statistikk.oppgave.HendelseType.RESERVERT
            HendelseType.AVRESERVERT -> no.nav.aap.statistikk.oppgave.HendelseType.AVRESERVERT
            HendelseType.LUKKET -> no.nav.aap.statistikk.oppgave.HendelseType.LUKKET
            HendelseType.OPPDATERT -> no.nav.aap.statistikk.oppgave.HendelseType.OPPDATERT
        },
        oppgaveId = requireNotNull(oppgaveDto.id) { "Trenger oppgave-ID for å skille mellom oppgavehendelser" },
        mottattTidspunkt = LocalDateTime.now(),
        personIdent = oppgaveDto.personIdent,
        saksnummer = oppgaveDto.saksnummer,
        behandlingRef = oppgaveDto.behandlingRef,
        journalpostId = oppgaveDto.journalpostId,
        enhet = oppgaveDto.enhet,
        oppfolgingsenhet = oppgaveDto.oppfølgingsenhet,
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

fun stringToNumber(string: String): Long {
    return IntStream.range(0, string.length)
        .mapToObj() { 10.0.pow(it.toDouble()) * string[it].code }
        .reduce { acc, curr -> acc + curr }.orElse(0.0).mod(1_000_000.0).roundToLong()
}