package no.nav.aap.statistikk.hendelser

import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.ÅrsakTilBehandling
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.hendelseLagret
import no.nav.aap.statistikk.nyBehandlingOpprettet
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakRepository
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
    private val sakStatistikkService: SaksStatistikkService,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    fun prosesserNyHendelse(hendelse: StoppetBehandling) {
        val person = personService.hentEllerLagrePerson(hendelse.ident)
        val sak = hentEllerSettInnSak(person, hendelse.saksnummer, hendelse.sakStatus)

        val behandlingId = hentEllerLagreBehandlingId(hendelse, sak)

        if (hendelse.behandlingStatus == Status.AVSLUTTET) {
            avsluttetBehandlingService.lagre(
                hendelse.avsluttetBehandling!!.tilDomene(
                    hendelse.saksnummer,
                    hendelse.behandlingReferanse,
                    hendelse.hendelsesTidspunkt,
                )
            )
        }

        val vedtakTid = hendelse.avklaringsbehov.utledVedtakTid()
        val ansvarligBeslutter = hendelse.avklaringsbehov.utledAnsvarligBeslutter()
        val erHosNAY = hendelse.avklaringsbehov.hosNAY()

        sakStatistikkService.lagreSakInfoTilBigquery(
            sak,
            behandlingId,
            hendelse.versjon,
            hendelse.hendelsesTidspunkt,
            vedtakTidspunkt = vedtakTid,
            erManuell = hendelse.avklaringsbehov.erManuell(),
            ansvarligBeslutter = ansvarligBeslutter,
            erHosNAY = erHosNAY
        )
        meterRegistry.hendelseLagret().increment()
        logger.info("Hendelse behandlet. Saksnr: ${hendelse.saksnummer}")
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
            gjeldendeAvklaringsBehov = dto.avklaringsbehov.utledGjeldendeAvklaringsBehov(),
            søknadsformat = dto.soknadsFormat.tilDomene(),
            venteÅrsak = dto.avklaringsbehov.utledÅrsakTilSattPåVent(),
            gjeldendeStegGruppe = dto.avklaringsbehov.utledGjeldendeStegType()?.gruppe,
            årsaker = dto.årsakTilBehandling.map { it.tilDomene() }
        )
        val eksisterendeBehandlingId = behandlingRepository.hent(dto.behandlingReferanse)?.id

        val relatertBehandlingUUID = dto.relatertBehandling
        val relatertBehadling =
            relatertBehandlingUUID?.let { behandlingRepository.hent(relatertBehandlingUUID) }

        val behandlingId = if (eksisterendeBehandlingId != null) {
            behandlingRepository.oppdaterBehandling(
                behandling.copy(
                    id = eksisterendeBehandlingId,
                    relatertBehandlingId = relatertBehadling?.id
                )
            )
            eksisterendeBehandlingId
        } else {
            val id =
                behandlingRepository.opprettBehandling(behandling.copy(relatertBehandlingId = relatertBehadling?.id))
            logger.info("Opprettet behandling")
            meterRegistry.nyBehandlingOpprettet(dto.behandlingType.tilDomene()).increment()
            id
        }
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
        ÅrsakTilBehandling.SØKNAD -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.SØKNAD
        ÅrsakTilBehandling.AKTIVITETSMELDING -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.AKTIVITETSMELDING
        ÅrsakTilBehandling.MELDEKORT -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.MELDEKORT
        ÅrsakTilBehandling.LEGEERKLÆRING -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.LEGEERKLÆRING
        ÅrsakTilBehandling.AVVIST_LEGEERKLÆRING -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.AVVIST_LEGEERKLÆRING
        ÅrsakTilBehandling.DIALOGMELDING -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.DIALOGMELDING
        ÅrsakTilBehandling.G_REGULERING -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.G_REGULERING
        ÅrsakTilBehandling.REVURDER_MEDLEMSKAP -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.REVURDER_MEDLEMSSKAP
        ÅrsakTilBehandling.REVURDER_YRKESSKADE -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.REVURDER_YRKESSKADE
        ÅrsakTilBehandling.REVURDER_BEREGNING -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.REVURDER_BEREGNING
        ÅrsakTilBehandling.REVURDER_LOVVALG -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.REVURDER_LOVVALG
        ÅrsakTilBehandling.KLAGE -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.KLAGE
        ÅrsakTilBehandling.REVURDER_SAMORDNING -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.REVURDER_SAMORDNING
        ÅrsakTilBehandling.LOVVALG_OG_MEDLEMSKAP -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.LOVVALG_OG_MEDLEMSKAP
        ÅrsakTilBehandling.FORUTGAENDE_MEDLEMSKAP -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.FORUTGAENDE_MEDLEMSKAP
        ÅrsakTilBehandling.SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND
        ÅrsakTilBehandling.BARNETILLEGG -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.BARNETILLEGG
        ÅrsakTilBehandling.INSTITUSJONSOPPHOLD -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.INSTITUSJONSOPPHOLD
        ÅrsakTilBehandling.SAMORDNING_OG_AVREGNING -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.SAMORDNING_OG_AVREGNING
        ÅrsakTilBehandling.REFUSJONSKRAV -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.REFUSJONSKRAV
        ÅrsakTilBehandling.UTENLANDSOPPHOLD_FOR_SOKNADSTIDSPUNKT -> no.nav.aap.statistikk.behandling.ÅrsakTilBehandling.UTENLANDSOPPHOLD_FOR_SOKNADSTIDSPUNKT
    }
}
