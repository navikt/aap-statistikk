package no.nav.aap.statistikk.api

import com.papsign.ktor.openapigen.modules.RouteOpenAPIModule
import com.papsign.ktor.openapigen.route.EndpointInfo
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.route.status
import io.ktor.http.*
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.TilbakekrevingsbehandlingOppdatertHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.dokumenter.TilbakekrevingBehandlingsstatus
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.statistikk.tilbakekreving.LagreTilbakekrevingHendelseJobb
import no.nav.aap.statistikk.tilbakekreving.TilbakekrevingBehandlingStatus
import no.nav.aap.statistikk.tilbakekreving.TilbakekrevingHendelse
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbSpesifikasjon
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.statistikk.OppgaveHendelse
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.jobber.LagreAvklaringsbehovHendelseJobb
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobb
import no.nav.aap.statistikk.oppgave.Oppgavestatus
import no.nav.aap.statistikk.postmottak.LagrePostmottakHendelseJobb
import no.nav.aap.tilgang.AuthorizationMachineToMachineConfig
import no.nav.aap.tilgang.authorizedPost
import java.time.LocalDateTime
import java.util.UUID
import java.util.stream.IntStream
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Generisk hjelpefunksjon for alle route-handlers som mottar en statistikk-hendelse.
 * Alle handlers gjør det samme: motta DTO → (valgfritt map) → serialiser → legg i kø → svar 202.
 */
inline fun <reified DTO : Any> NormalOpenAPIRoute.mottaHendelse(
    path: String,
    authorizedAzps: List<UUID>,
    transactionExecutor: TransactionExecutor,
    jobbAppender: JobbAppender,
    jobbSpesifikasjon: JobbSpesifikasjon,
    crossinline saksnummer: (DTO) -> Long?,
    extraModules: List<RouteOpenAPIModule> = emptyList(),
    crossinline mapPayload: (DTO) -> Any = { it },
) {
    val modules = (listOf(TagModule(listOf(Tags.MottaStatistikk))) + extraModules).toTypedArray()
    route(path).status(HttpStatusCode.Accepted) {
        authorizedPost<Unit, String, DTO>(
            modules = modules,
            routeConfig = AuthorizationMachineToMachineConfig(authorizedAzps = authorizedAzps)
        ) { _, dto ->
            transactionExecutor.withinTransaction { conn ->
                val sak = saksnummer(dto)
                var input = JobbInput(jobbSpesifikasjon)
                    .medPayload(DefaultJsonMapper.toJson(mapPayload(dto)))
                    .medCallId()
                if (sak != null) input = input.forSak(sak)
                jobbAppender.leggTil(conn, input)
            }
            responder.respond(HttpStatusCode.Accepted, "{}", pipeline)
        }
    }
}

fun NormalOpenAPIRoute.mottaStoppetBehandling(
    transactionExecutor: TransactionExecutor,
    jobbAppender: JobbAppender,
    lagreStoppetHendelseJobb: LagreStoppetHendelseJobb,
) {
    mottaHendelse<StoppetBehandling>(
        path = "/stoppetBehandling",
        authorizedAzps = listOf(Azp.Behandlingsflyt.uuid),
        transactionExecutor = transactionExecutor,
        jobbAppender = jobbAppender,
        jobbSpesifikasjon = lagreStoppetHendelseJobb,
        saksnummer = { stringToNumber(it.saksnummer) },
    )
}

fun NormalOpenAPIRoute.mottaTilbakekrevingshendelse(
    transactionExecutor: TransactionExecutor,
    jobbAppender: JobbAppender,
) {
    mottaHendelse<TilbakekrevingsbehandlingOppdatertHendelse>(
        path = "/tilbakekrevingshendelse",
        authorizedAzps = listOf(Azp.Behandlingsflyt.uuid),
        transactionExecutor = transactionExecutor,
        jobbAppender = jobbAppender,
        jobbSpesifikasjon = LagreTilbakekrevingHendelseJobb(),
        saksnummer = { stringToNumber(it.saksnummer.toString()) },
        mapPayload = { it.tilDomene() },
    )
}

fun NormalOpenAPIRoute.mottaOppdatertBehandling(
    transactionExecutor: TransactionExecutor,
    jobbAppender: JobbAppender,
    lagreAvklaringsbehovHendelseJobb: LagreAvklaringsbehovHendelseJobb,
) {
    mottaHendelse<StoppetBehandling>(
        path = "/oppdatertBehandling",
        authorizedAzps = listOf(Azp.Behandlingsflyt.uuid),
        transactionExecutor = transactionExecutor,
        jobbAppender = jobbAppender,
        jobbSpesifikasjon = lagreAvklaringsbehovHendelseJobb,
        saksnummer = { stringToNumber(it.saksnummer) },
    )
}

fun NormalOpenAPIRoute.mottaOppgaveOppdatering(
    transactionExecutor: TransactionExecutor,
    jobbAppender: JobbAppender,
) {
    mottaHendelse<OppgaveHendelse>(
        path = "/oppgave",
        authorizedAzps = listOf(Azp.Oppgave.uuid),
        transactionExecutor = transactionExecutor,
        jobbAppender = jobbAppender,
        jobbSpesifikasjon = LagreOppgaveHendelseJobb(),
        saksnummer = { it.oppgaveTilStatistikkDto.saksnummer?.let { s -> stringToNumber(s) } },
        extraModules = listOf(EndpointInfo("OppgaveHendelse")),
        mapPayload = { it.tilDomene() },
    )
}

fun NormalOpenAPIRoute.mottaPostmottakOppdatering(
    transactionExecutor: TransactionExecutor,
    jobbAppender: JobbAppender,
) {
    mottaHendelse<DokumentflytStoppetHendelse>(
        path = "/postmottak",
        authorizedAzps = listOf(Azp.Postmottak.uuid),
        transactionExecutor = transactionExecutor,
        jobbAppender = jobbAppender,
        jobbSpesifikasjon = LagrePostmottakHendelseJobb(),
        saksnummer = { it.journalpostId.referanse },
        extraModules = listOf(EndpointInfo("DokumentflytStoppetHendelse")),
    )
}

private fun OppgaveHendelse.tilDomene(): no.nav.aap.statistikk.oppgave.OppgaveHendelse {
    val nyOppgaveDto = this.oppgaveTilStatistikkDto
    return no.nav.aap.statistikk.oppgave.OppgaveHendelse(
        hendelse = when (this.hendelse) {
            HendelseType.OPPRETTET -> no.nav.aap.statistikk.oppgave.HendelseType.OPPRETTET
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
        harHasteMarkering = nyOppgaveDto.harHasteMarkering,
        sendtTid = this.sendtTidspunkt,
        versjon = nyOppgaveDto.versjon
    )
}

fun stringToNumber(string: String): Long {
    return IntStream.range(0, string.length)
        .mapToObj() { 10.0.pow(it.toDouble()) * string[it].code }
        .reduce { acc, curr -> acc + curr }.orElse(0.0).mod(1_000_000.0).roundToLong()
}

private fun TilbakekrevingsbehandlingOppdatertHendelse.tilDomene(): TilbakekrevingHendelse {
    return TilbakekrevingHendelse(
        saksnummer = Saksnummer(saksnummer.toString()),
        behandlingRef = behandlingref.referanse.toString(),
        behandlingStatus = behandlingStatus.tilDomene(),
        sakOpprettet = sakOpprettet,
        totaltFeilutbetaltBeløp = totaltFeilutbetaltBeløp,
        saksbehandlingURL = saksbehandlingURL,
        opprettetTid = LocalDateTime.now(),
    )
}

private fun TilbakekrevingBehandlingsstatus.tilDomene(): TilbakekrevingBehandlingStatus {
    return when (this) {
        TilbakekrevingBehandlingsstatus.OPPRETTET -> TilbakekrevingBehandlingStatus.OPPRETTET
        TilbakekrevingBehandlingsstatus.TIL_BEHANDLING -> TilbakekrevingBehandlingStatus.TIL_BEHANDLING
        TilbakekrevingBehandlingsstatus.TIL_GODKJENNING -> TilbakekrevingBehandlingStatus.TIL_GODKJENNING
        TilbakekrevingBehandlingsstatus.TIL_BESLUTTER -> TilbakekrevingBehandlingStatus.TIL_BESLUTTER
        TilbakekrevingBehandlingsstatus.RETUR_FRA_BESLUTTER -> TilbakekrevingBehandlingStatus.RETUR_FRA_BESLUTTER
        TilbakekrevingBehandlingsstatus.AVSLUTTET -> TilbakekrevingBehandlingStatus.AVSLUTTET
    }
}