package no.nav.aap.statistikk.hendelser

import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.ÅrsakTilBehandling
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.*
import no.nav.aap.statistikk.hendelseLagret
import no.nav.aap.statistikk.nyBehandlingOpprettet
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakRepository
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.verdityper.dokument.Kanal
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import no.nav.aap.behandlingsflyt.kontrakt.sak.Status as SakStatus

private val logger = LoggerFactory.getLogger("HendelsesService")

class HendelsesService(
    private val sakRepository: SakRepository,
    private val avsluttetBehandlingService: AvsluttetBehandlingService,
    private val personService: PersonService,
    private val behandlingRepository: IBehandlingRepository,
    private val meterRegistry: MeterRegistry,
    private val opprettBigQueryLagringCallback: (BehandlingId) -> Unit,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    fun prosesserNyHendelse(hendelse: StoppetBehandling) {
        val person = personService.hentEllerLagrePerson(hendelse.ident)
        val saksnummer = hendelse.saksnummer.let(::Saksnummer)

        val sak =
            hentEllerSettInnSak(person, saksnummer, hendelse.sakStatus)

        val behandlingId = hentEllerLagreBehandlingId(hendelse, sak)

        if (hendelse.behandlingStatus == Status.AVSLUTTET) {
            // TODO: legg denne i en jobb
            val avsluttetBehandling =
                requireNotNull(hendelse.avsluttetBehandling) { "Om behandlingen er avsluttet, så må avsluttetBehandling være ikke-null." }
            avsluttetBehandlingService.lagre(
                avsluttetBehandling.tilDomene(
                    saksnummer,
                    hendelse.behandlingReferanse,
                )
            )
        }

        opprettBigQueryLagringCallback(behandlingId)

        meterRegistry.hendelseLagret().increment()
        logger.info("Hendelse behandlet. Saksnr: ${hendelse.saksnummer}")
    }

    private fun hentEllerLagreBehandlingId(
        dto: StoppetBehandling,
        sak: Sak
    ): Long {

        if (!Miljø.erProd()) {
            logger.info("Hent eller lagrer for sak ${sak.id}. DTO: $dto")
        }

        val behandling = Behandling(
            referanse = dto.behandlingReferanse,
            sak = sak,
            typeBehandling = dto.behandlingType.tilDomene(),
            opprettetTid = dto.behandlingOpprettetTidspunkt,
            vedtakstidspunkt = dto.avklaringsbehov.utledVedtakTid(),
            ansvarligBeslutter = dto.avklaringsbehov.utledAnsvarligBeslutter(),
            mottattTid = dto.mottattTid.truncatedTo(ChronoUnit.SECONDS),
            status = dto.behandlingStatus.tilDomene(),
            versjon = Versjon(verdi = dto.versjon),
            relaterteIdenter = dto.identerForSak,
            sisteSaksbehandler = dto.avklaringsbehov.sistePersonPåBehandling(),
            gjeldendeAvklaringsBehov = dto.avklaringsbehov.utledGjeldendeAvklaringsBehov(),
            gjeldendeAvklaringsbehovStatus = dto.avklaringsbehov.sisteAvklaringsbehovStatus(),
            søknadsformat = dto.soknadsFormat.tilDomene(),
            venteÅrsak = dto.avklaringsbehov.utledÅrsakTilSattPåVent(),
            returÅrsak = dto.avklaringsbehov.årsakTilRetur()?.name,
            resultat = dto.avsluttetBehandling?.resultat.resultatTilDomene(),
            gjeldendeStegGruppe = dto.avklaringsbehov.utledGjeldendeStegType()?.gruppe,
            årsaker = dto.årsakTilBehandling.map { it.tilDomene() }
        )
        val eksisterendeBehandlingId = behandlingRepository.hent(dto.behandlingReferanse)?.id

        val relatertBehadling = hentRelatertBehandling(dto)

        val behandlingId = if (eksisterendeBehandlingId != null) {
            behandlingRepository.oppdaterBehandling(
                behandling.copy(
                    id = eksisterendeBehandlingId,
                    relatertBehandlingId = relatertBehadling?.id
                )
            )
            logger.info("Oppdaterte behandling med referanse ${behandling.referanse} og id $eksisterendeBehandlingId.")
            eksisterendeBehandlingId
        } else {
            val id =
                behandlingRepository.opprettBehandling(behandling.copy(relatertBehandlingId = relatertBehadling?.id))
            logger.info("Opprettet behandling med referanse ${behandling.referanse} og id $id.")
            meterRegistry.nyBehandlingOpprettet(dto.behandlingType.tilDomene()).increment()
            id
        }
        return behandlingId
    }

    private fun hentRelatertBehandling(dto: StoppetBehandling): Behandling? {
        val relatertBehandlingUUID = dto.relatertBehandling
        val relatertBehadling =
            relatertBehandlingUUID?.let { behandlingRepository.hent(relatertBehandlingUUID) }
        return relatertBehadling
    }

    private fun hentEllerSettInnSak(
        person: Person,
        saksnummer: Saksnummer,
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
        } else {
            sakRepository.oppdaterSak(
                sak.copy(
                    sakStatus = sakStatus.tilDomene(),
                    sistOppdatert = LocalDateTime.now(clock)
                )
            )
        }
        return sak
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

fun Status.tilDomene(): BehandlingStatus =
    when (this) {
        Status.OPPRETTET -> BehandlingStatus.OPPRETTET
        Status.UTREDES -> BehandlingStatus.UTREDES
        Status.IVERKSETTES -> BehandlingStatus.IVERKSETTES
        Status.AVSLUTTET -> BehandlingStatus.AVSLUTTET
    }

fun Kanal.tilDomene(): SøknadsFormat {
    return when (this) {
        Kanal.DIGITAL -> SøknadsFormat.DIGITAL
        Kanal.PAPIR -> SøknadsFormat.PAPIR
    }
}

fun ÅrsakTilBehandling.tilDomene(): no.nav.aap.statistikk.behandling.ÅrsakTilBehandling {
    return when (this) {
        ÅrsakTilBehandling.SØKNAD -> SØKNAD
        ÅrsakTilBehandling.AKTIVITETSMELDING -> AKTIVITETSMELDING
        ÅrsakTilBehandling.MELDEKORT -> MELDEKORT
        ÅrsakTilBehandling.LEGEERKLÆRING -> LEGEERKLÆRING
        ÅrsakTilBehandling.AVVIST_LEGEERKLÆRING -> AVVIST_LEGEERKLÆRING
        ÅrsakTilBehandling.DIALOGMELDING -> DIALOGMELDING
        ÅrsakTilBehandling.G_REGULERING -> G_REGULERING
        ÅrsakTilBehandling.REVURDER_MEDLEMSKAP -> REVURDER_MEDLEMSSKAP
        ÅrsakTilBehandling.REVURDER_YRKESSKADE -> REVURDER_YRKESSKADE
        ÅrsakTilBehandling.REVURDER_BEREGNING -> REVURDER_BEREGNING
        ÅrsakTilBehandling.REVURDER_LOVVALG -> REVURDER_LOVVALG
        ÅrsakTilBehandling.KLAGE -> KLAGE
        ÅrsakTilBehandling.REVURDER_SAMORDNING -> REVURDER_SAMORDNING
        ÅrsakTilBehandling.LOVVALG_OG_MEDLEMSKAP -> LOVVALG_OG_MEDLEMSKAP
        ÅrsakTilBehandling.FORUTGAENDE_MEDLEMSKAP -> FORUTGAENDE_MEDLEMSKAP
        ÅrsakTilBehandling.SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND -> SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND
        ÅrsakTilBehandling.BARNETILLEGG -> BARNETILLEGG
        ÅrsakTilBehandling.INSTITUSJONSOPPHOLD -> INSTITUSJONSOPPHOLD
        ÅrsakTilBehandling.SAMORDNING_OG_AVREGNING -> SAMORDNING_OG_AVREGNING
        ÅrsakTilBehandling.REFUSJONSKRAV -> REFUSJONSKRAV
        ÅrsakTilBehandling.UTENLANDSOPPHOLD_FOR_SOKNADSTIDSPUNKT -> UTENLANDSOPPHOLD_FOR_SOKNADSTIDSPUNKT
        ÅrsakTilBehandling.SØKNAD_TRUKKET -> SØKNAD_TRUKKET
        ÅrsakTilBehandling.VURDER_RETTIGHETSPERIODE -> VURDER_RETTIGHETSPERIODE
    }
}
