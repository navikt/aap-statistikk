package no.nav.aap.statistikk.api

import com.papsign.ktor.openapigen.route.EndpointInfo
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.route.status
import io.ktor.http.*
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
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

private val log = LoggerFactory.getLogger("MottaStatistikk")


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
        authorizedPost<Unit, String, OppgaveHendelse>(
            modules = arrayOf(
                TagModule(listOf(Tags.MottaStatistikk)),
                EndpointInfo("OppgaveHendelse")
            ),
            routeConfig = AuthorizationMachineToMachineConfig(authorizedAzps = listOf(Azp.Oppgave.uuid)),

            ) { _, dto ->
            transactionExecutor.withinTransaction { conn ->
                val saksnummer = dto.oppgaveTilStatistikkDto.saksnummer
                val encodedSaksNummer = saksnummer?.let { stringToNumber(it) }

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
        authorizedPost<Unit, String, DokumentflytStoppetHendelse>(
            modules = arrayOf(
                TagModule(listOf(Tags.MottaStatistikk)),
                EndpointInfo("DokumentflytStoppetHendelse")
            ),
            routeConfig = AuthorizationMachineToMachineConfig(authorizedAzps = listOf(Azp.Postmottak.uuid))
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
    val nyOppgaveDto = this.oppgaveTilStatistikkDto
    return no.nav.aap.statistikk.oppgave.OppgaveHendelse(
        hendelse = when (this.hendelse) {
            HendelseType.OPPRETTET -> no.nav.aap.statistikk.oppgave.HendelseType.OPPRETTET
            HendelseType.GJENÅPNET -> no.nav.aap.statistikk.oppgave.HendelseType.OPPDATERT
            HendelseType.RESERVERT -> no.nav.aap.statistikk.oppgave.HendelseType.RESERVERT
            HendelseType.AVRESERVERT -> no.nav.aap.statistikk.oppgave.HendelseType.AVRESERVERT
            HendelseType.LUKKET -> no.nav.aap.statistikk.oppgave.HendelseType.LUKKET
            HendelseType.OPPDATERT -> no.nav.aap.statistikk.oppgave.HendelseType.OPPDATERT
        },
        oppgaveId = requireNotNull(nyOppgaveDto.id) { "Trenger oppgave-ID for å skille mellom oppgavehendelser" },
        mottattTidspunkt = LocalDateTime.now(),
        personIdent = nyOppgaveDto.personIdent,
        saksnummer = nyOppgaveDto.saksnummer,
        behandlingRef = nyOppgaveDto.behandlingRef,
        journalpostId = nyOppgaveDto.journalpostId,
        enhet = nyOppgaveDto.enhet,
        avklaringsbehovKode = nyOppgaveDto.avklaringsbehovKode,
        status = when (nyOppgaveDto.status) {
            no.nav.aap.oppgave.verdityper.Status.OPPRETTET -> Oppgavestatus.OPPRETTET
            no.nav.aap.oppgave.verdityper.Status.AVSLUTTET -> Oppgavestatus.AVSLUTTET
        },
        reservertAv = nyOppgaveDto.reservertAv,
        reservertTidspunkt = nyOppgaveDto.reservertTidspunkt,
        opprettetTidspunkt = nyOppgaveDto.opprettetTidspunkt,
        endretAv = nyOppgaveDto.endretAv,
        endretTidspunkt = nyOppgaveDto.endretTidspunkt,
    )
}

fun stringToNumber(string: String): Long {
    return IntStream.range(0, string.length)
        .mapToObj() { 10.0.pow(it.toDouble()) * string[it].code }
        .reduce { acc, curr -> acc + curr }.orElse(0.0).mod(1_000_000.0).roundToLong()
}