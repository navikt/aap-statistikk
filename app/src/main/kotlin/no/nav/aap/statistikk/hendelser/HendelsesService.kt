package no.nav.aap.statistikk.hendelser

import io.micrometer.core.instrument.Counter
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.sak.Status as SakStatus
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.person.IPersonRepository
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit


class HendelsesService(
    private val sakRepository: SakRepository,
    private val avsluttetBehandlingService: AvsluttetBehandlingService,
    private val personRepository: IPersonRepository,
    private val behandlingRepository: IBehandlingRepository,
    private val hendelseLagretCounter: Counter,
    private val sakStatistikkService: SaksStatistikkService,
    private val clock: Clock = Clock.systemUTC()
) {
    fun prosesserNyHendelse(hendelse: StoppetBehandling) {
        val person = hentEllerSettInnPerson(hendelse.ident)
        val sak = hentEllerSettInnSak(person, hendelse.saksnummer, hendelse.sakStatus)

        val behandlingId = hentEllerLagreBehandlingId(hendelse, sak)

        if (hendelse.behandlingStatus == Status.AVSLUTTET) {
            avsluttetBehandlingService.lagre(hendelse.avsluttetBehandling!!.tilDomene())
        }

        val vedtakTid = hendelse.avklaringsbehov.utledVedtakTid()

        sakStatistikkService.lagreSakInfoTilBigquery(
            sak,
            behandlingId,
            hendelse.versjon,
            hendelse.hendelsesTidspunkt,
            vedtakTidspunkt = vedtakTid
        )
        hendelseLagretCounter.increment()
    }

    private fun hentEllerLagreBehandlingId(
        dto: StoppetBehandling,
        sak: Sak
    ): Long {
        val behandling = Behandling(
            referanse = dto.behandlingReferanse,
            sak = sak,
            typeBehandling = dto.behandlingType.tilDomene(),
            opprettetTid = dto.behandlingOpprettetTidspunkt,
            mottattTid = dto.mottattTid.truncatedTo(ChronoUnit.SECONDS),
            status = dto.behandlingStatus.tilDomene(),
            versjon = Versjon(verdi = dto.versjon),
            relaterteIdenter = dto.identerForSak,
            sisteSaksbehandler = dto.avklaringsbehov.sistePersonPåBehandling(),
            gjeldendeAvklaringsBehov = dto.avklaringsbehov.utledGjeldendeAvklaringsBehov()
        )
        val eksisterendeBehandlingId = behandlingRepository.hent(dto.behandlingReferanse)?.id

        val relatertBehandlingUUID = dto.relatertBehandling
        val relatertBehadling =
            relatertBehandlingUUID?.let { behandlingRepository.hent(relatertBehandlingUUID) }

        val behandlingId =
            eksisterendeBehandlingId
                ?.also {
                    behandlingRepository.oppdaterBehandling(
                        behandling.copy(
                            id = eksisterendeBehandlingId,
                            relatertBehandlingId = relatertBehadling?.id
                        )
                    )
                }
                ?: behandlingRepository.opprettBehandling(behandling.copy(relatertBehandlingId = relatertBehadling?.id))
        return behandlingId
    }

    private fun hentEllerSettInnSak(
        person: Person,
        saksnummer: String,
        sakStatus: SakStatus
    ): Sak {
        var sak = sakRepository.hentSakEllernull(saksnummer)
        if (sak == null) {
            val sakId = sakRepository.settInnSak(
                Sak(
                    id = null,
                    saksnummer = saksnummer,
                    person = person,
                    sistOppdatert = LocalDateTime.now(clock),
                    sakStatus = sakStatus.tilDomene()
                )
            )
            sak = sakRepository.hentSak(sakId)
        }
        return sak
    }

    private fun hentEllerSettInnPerson(ident: String): Person {
        var person = personRepository.hentPerson(ident)
        if (person == null) {
            personRepository.lagrePerson(Person(ident))
        }
        person = personRepository.hentPerson(ident)!!
        return person
    }
}

private fun SakStatus.tilDomene(): no.nav.aap.statistikk.sak.SakStatus {
    return when (this) {
        SakStatus.OPPRETTET -> no.nav.aap.statistikk.sak.SakStatus.OPPRETTET
        SakStatus.UTREDES -> no.nav.aap.statistikk.sak.SakStatus.UTREDES
        SakStatus.LØPENDE -> no.nav.aap.statistikk.sak.SakStatus.LØPENDE
        SakStatus.AVSLUTTET -> no.nav.aap.statistikk.sak.SakStatus.AVSLUTTET
    }
}

fun no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.tilDomene(): TypeBehandling =
    when (this) {
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Førstegangsbehandling -> TypeBehandling.Førstegangsbehandling
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Revurdering -> TypeBehandling.Revurdering
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Tilbakekreving -> TypeBehandling.Tilbakekreving
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Klage -> TypeBehandling.Klage
    }

fun Status.tilDomene(): no.nav.aap.statistikk.behandling.BehandlingStatus =
    when (this) {
        Status.OPPRETTET -> no.nav.aap.statistikk.behandling.BehandlingStatus.OPPRETTET
        Status.UTREDES -> no.nav.aap.statistikk.behandling.BehandlingStatus.UTREDES
        Status.IVERKSETTES -> no.nav.aap.statistikk.behandling.BehandlingStatus.IVERKSETTES
        Status.AVSLUTTET -> no.nav.aap.statistikk.behandling.BehandlingStatus.AVSLUTTET
    }
