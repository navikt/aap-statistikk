package no.nav.aap.statistikk.hendelser

import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.behandling.Vurderingsbehov.*
import no.nav.aap.statistikk.hendelseLagret
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.nyBehandlingOpprettet
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.sak.SakService
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.verdityper.dokument.Kanal
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.*
import no.nav.aap.behandlingsflyt.kontrakt.sak.Status as SakStatus

class HendelsesService(
    private val sakService: SakService,
    private val avsluttetBehandlingService: AvsluttetBehandlingService,
    private val personService: PersonService,
    private val behandlingRepository: IBehandlingRepository,
    private val meterRegistry: MeterRegistry,
    private val opprettBigQueryLagringSakStatistikkCallback: (BehandlingId) -> Unit,
    private val opprettRekjørSakstatistikkCallback: (BehandlingId) -> Unit,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        fun konstruer(
            connection: DBConnection,
            avsluttetBehandlingService: AvsluttetBehandlingService,
            jobbAppender: JobbAppender,
            meterRegistry: MeterRegistry,
            clock: Clock = Clock.systemDefaultZone()
        ): HendelsesService {
            return HendelsesService(
                sakService = SakService(SakRepositoryImpl(connection)),
                personService = PersonService(PersonRepository(connection)),
                avsluttetBehandlingService = avsluttetBehandlingService,
                behandlingRepository = BehandlingRepository(connection, clock),
                meterRegistry = meterRegistry,
                opprettBigQueryLagringSakStatistikkCallback = {
                    LoggerFactory.getLogger(HendelsesService::class.java)
                        .info("Legger til lagretilsaksstatistikkjobb. BehandlingId: $it")
                    jobbAppender.leggTilLagreSakTilBigQueryJobb(
                        connection,
                        it
                    )
                },
                opprettRekjørSakstatistikkCallback = {
                    LoggerFactory.getLogger(HendelsesService::class.java)
                        .info("Starter resending-jobb. BehandlingId: $it")
                    jobbAppender.leggTilResendSakstatistikkJobb(connection, it)
                })
        }
    }

    fun prosesserNyHendelse(hendelse: StoppetBehandling) {
        val person = personService.hentEllerLagrePerson(hendelse.ident)
        val saksnummer = hendelse.saksnummer.let(::Saksnummer)

        val sak =
            sakService.hentEllerSettInnSak(person, saksnummer, hendelse.sakStatus.tilDomene())

        val behandlingId = hentEllerLagreBehandling(hendelse, sak).id!!

        if (hendelse.behandlingStatus == Status.AVSLUTTET) {
            // TODO: legg denne i en jobb
            val avsluttetBehandling =
                requireNotNull(hendelse.avsluttetBehandling) { "Om behandlingen er avsluttet, så må avsluttetBehandling være ikke-null." }

            // Oppfølgingsbehandling er ikke relatert til en ytelse, så dette kan ignoreres.
            if (hendelse.behandlingType !in listOf(no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.OppfølgingsBehandling)) {
                avsluttetBehandlingService.lagre(
                    avsluttetBehandling.tilDomene(
                        saksnummer,
                        hendelse.behandlingReferanse,
                    )
                )
            }
        }

        if (hendelse.behandlingType !in listOf(no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.OppfølgingsBehandling)) {
            opprettBigQueryLagringSakStatistikkCallback(behandlingId)
        }

        meterRegistry.hendelseLagret().increment()
        logger.info("Hendelse behandlet. Saksnr: ${hendelse.saksnummer}")
    }

    fun prosesserNyHistorikkHendelse(hendelse: StoppetBehandling) {
        val person = personService.hentEllerLagrePerson(hendelse.ident)
        val saksnummer = hendelse.saksnummer.let(::Saksnummer)

        val sak =
            sakService.hentEllerSettInnSak(person, saksnummer, hendelse.sakStatus.tilDomene())
        val behandling = konstruerBehandling(hendelse, sak)

        val behandlingMedHistorikk =
            ReberegnHistorikk().avklaringsbehovTilHistorikk(hendelse, behandling)

        check(behandling.status == behandlingMedHistorikk.status)
        { "Behandlingstatus er ikke lik behandling med historikk-status. Behandlingstatus: ${behandling.status}, behandling med historikk-status: ${behandlingMedHistorikk.status}" }

        val behandlingId = checkNotNull(hentEllerLagreBehandling(hendelse, sak).id)

        val behandlingMedId = behandlingMedHistorikk.copy(id = behandlingId)

        behandlingRepository.invaliderOgLagreNyHistorikk(behandlingMedId)

        logger.info("Starter jobb for rekjøring av saksstatistikk for behandling med id ${behandlingMedId.id}.")

        opprettRekjørSakstatistikkCallback(behandlingId)
    }


    private fun hentEllerLagreBehandling(
        dto: StoppetBehandling,
        sak: Sak
    ): Behandling {

        if (!Miljø.erProd()) {
            logger.info("Hent eller lagrer for sak ${sak.id}. DTO: $dto")
        }

        val behandling = konstruerBehandling(dto, sak)

        val eksisterendeBehandlingId = behandling.id
        val behandlingId = if (eksisterendeBehandlingId != null) {
            behandlingRepository.oppdaterBehandling(
                behandling.copy(id = eksisterendeBehandlingId)
            )
            logger.info("Oppdaterte behandling med referanse ${behandling.referanse} og id $eksisterendeBehandlingId.")
            eksisterendeBehandlingId
        } else {
            val id = behandlingRepository.opprettBehandling(behandling)
            logger.info("Opprettet behandling med referanse ${behandling.referanse} og id $id.")
            meterRegistry.nyBehandlingOpprettet(dto.behandlingType.tilDomene()).increment()
            id
        }
        return behandling.copy(id = behandlingId)
    }

    private fun konstruerBehandling(
        dto: StoppetBehandling,
        sak: Sak
    ): Behandling {
        val behandling = Behandling(
            referanse = dto.behandlingReferanse,
            sak = sak,
            typeBehandling = dto.behandlingType.tilDomene(),
            opprettetTid = dto.behandlingOpprettetTidspunkt,
            vedtakstidspunkt = dto.avklaringsbehov.utledVedtakTid(),
            ansvarligBeslutter = dto.avklaringsbehov.utledAnsvarligBeslutter(),
            mottattTid = dto.mottattTid,
            status = dto.behandlingStatus.tilDomene(),
            versjon = Versjon(verdi = dto.versjon),
            relaterteIdenter = dto.identerForSak,
            sisteSaksbehandler = dto.avklaringsbehov.sistePersonPåBehandling(),
            gjeldendeAvklaringsBehov = dto.avklaringsbehov.utledGjeldendeAvklaringsBehov()?.kode?.name,
            gjeldendeAvklaringsbehovStatus = dto.avklaringsbehov.sisteAvklaringsbehovStatus(),
            søknadsformat = dto.soknadsFormat.tilDomene(),
            venteÅrsak = dto.avklaringsbehov.utledÅrsakTilSattPåVent(),
            returÅrsak = dto.avklaringsbehov.årsakTilRetur()?.name,
            resultat = dto.avsluttetBehandling?.resultat.resultatTilDomene(),
            gjeldendeStegGruppe = dto.avklaringsbehov.utledGjeldendeStegType()?.gruppe,
            årsaker = dto.vurderingsbehov.map { it.tilDomene() },
            oppdatertTidspunkt = dto.hendelsesTidspunkt
        )
        val eksisterendeBehandlingId = behandlingRepository.hent(dto.behandlingReferanse)?.id

        val relatertBehadling = hentRelatertBehandling(dto.relatertBehandling)
        val behandlingMedRelatertBehandling =
            behandling.copy(relatertBehandlingId = relatertBehadling?.id)
        return behandlingMedRelatertBehandling.copy(eksisterendeBehandlingId)
    }

    private fun hentRelatertBehandling(relatertBehandlingUUID: UUID?): Behandling? {
        val relatertBehadling =
            relatertBehandlingUUID?.let { behandlingRepository.hent(relatertBehandlingUUID) }
        return relatertBehadling
    }
}

internal fun SakStatus.tilDomene(): no.nav.aap.statistikk.sak.SakStatus {
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
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.SvarFraAndreinstans -> TypeBehandling.SvarFraAndreinstans
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.OppfølgingsBehandling -> TypeBehandling.Oppfølgingsbehandling
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Aktivitetsplikt -> TypeBehandling.Aktivitetsplikt
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Aktivitetsplikt11_9 -> TypeBehandling.Aktivitetsplikt11_9
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

fun Vurderingsbehov.tilDomene(): no.nav.aap.statistikk.behandling.Vurderingsbehov {
    return when (this) {
        Vurderingsbehov.SØKNAD -> SØKNAD
        Vurderingsbehov.AKTIVITETSMELDING -> AKTIVITETSMELDING
        Vurderingsbehov.MELDEKORT -> MELDEKORT
        Vurderingsbehov.FRITAK_MELDEPLIKT -> MELDEKORT
        Vurderingsbehov.LEGEERKLÆRING -> LEGEERKLÆRING
        Vurderingsbehov.AVVIST_LEGEERKLÆRING -> AVVIST_LEGEERKLÆRING
        Vurderingsbehov.DIALOGMELDING -> DIALOGMELDING
        Vurderingsbehov.G_REGULERING -> G_REGULERING
        Vurderingsbehov.REVURDER_MEDLEMSKAP -> REVURDER_MEDLEMSSKAP
        Vurderingsbehov.REVURDER_YRKESSKADE -> REVURDER_YRKESSKADE
        Vurderingsbehov.REVURDER_BEREGNING -> REVURDER_BEREGNING
        Vurderingsbehov.REVURDER_LOVVALG -> REVURDER_LOVVALG
        Vurderingsbehov.KLAGE -> KLAGE
        Vurderingsbehov.REVURDER_SAMORDNING -> REVURDER_SAMORDNING
        Vurderingsbehov.LOVVALG_OG_MEDLEMSKAP -> LOVVALG_OG_MEDLEMSKAP
        Vurderingsbehov.FORUTGAENDE_MEDLEMSKAP -> FORUTGAENDE_MEDLEMSKAP
        Vurderingsbehov.SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND -> SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND
        Vurderingsbehov.BARNETILLEGG -> BARNETILLEGG
        Vurderingsbehov.INSTITUSJONSOPPHOLD -> INSTITUSJONSOPPHOLD
        Vurderingsbehov.SAMORDNING_OG_AVREGNING -> SAMORDNING_OG_AVREGNING
        Vurderingsbehov.REFUSJONSKRAV -> REFUSJONSKRAV
        Vurderingsbehov.UTENLANDSOPPHOLD_FOR_SOKNADSTIDSPUNKT -> UTENLANDSOPPHOLD_FOR_SOKNADSTIDSPUNKT
        Vurderingsbehov.SØKNAD_TRUKKET -> SØKNAD_TRUKKET
        Vurderingsbehov.VURDER_RETTIGHETSPERIODE -> VURDER_RETTIGHETSPERIODE
        Vurderingsbehov.REVURDER_MANUELL_INNTEKT -> REVURDER_MANUELL_INNTEKT
        Vurderingsbehov.KLAGE_TRUKKET -> KLAGE_TRUKKET
        Vurderingsbehov.MOTTATT_KABAL_HENDELSE -> MOTTATT_KABAL_HENDELSE
        Vurderingsbehov.OPPFØLGINGSOPPGAVE -> OPPFØLGINGSOPPGAVE
        Vurderingsbehov.HELHETLIG_VURDERING -> HELHETLIG_VURDERING
        Vurderingsbehov.REVURDER_MELDEPLIKT_RIMELIG_GRUNN -> REVURDER_MELDEPLIKT_RIMELIG_GRUNN
        Vurderingsbehov.AKTIVITETSPLIKT_11_7 -> AKTIVITETSPLIKT_11_7
        Vurderingsbehov.AKTIVITETSPLIKT_11_9 -> AKTIVITETSPLIKT_11_9
        Vurderingsbehov.EFFEKTUER_AKTIVITETSPLIKT -> EFFEKTUER_AKTIVITETSPLIKT
        Vurderingsbehov.EFFEKTUER_AKTIVITETSPLIKT_11_9 -> EFFEKTUER_AKTIVITETSPLIKT_11_9
        Vurderingsbehov.OVERGANG_UFORE -> OVERGANG_UFORE
        Vurderingsbehov.OVERGANG_ARBEID -> OVERGANG_ARBEID
        Vurderingsbehov.REVURDERING_AVBRUTT -> REVURDERING_AVBRUTT
        Vurderingsbehov.DØDSFALL_BRUKER -> DØDSFALL_BRUKER
        Vurderingsbehov.DØDSFALL_BARN -> DØDSFALL_BARN
        Vurderingsbehov.OPPHOLDSKRAV -> OPPHOLDSKRAV
        Vurderingsbehov.REVURDER_STUDENT -> REVURDER_STUDENT
    }
}
