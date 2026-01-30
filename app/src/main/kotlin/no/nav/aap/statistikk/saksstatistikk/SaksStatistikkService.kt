package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.avsluttetbehandling.IRettighetstypeperiodeRepository
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.hendelser.returnert
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepository
import no.nav.aap.statistikk.sak.IBigQueryKvitteringRepository
import no.nav.aap.statistikk.sakDuplikat
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.årsakTilOpprettelseIkkeSatt
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Clock.systemDefaultZone
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class SaksStatistikkService(
    private val behandlingService: BehandlingService,
    private val rettighetstypeperiodeRepository: IRettighetstypeperiodeRepository,
    private val bigQueryKvitteringRepository: IBigQueryKvitteringRepository,
    private val skjermingService: SkjermingService,
    private val oppgaveHendelseRepository: OppgaveHendelseRepository,
    private val sakstatistikkRepository: SakstatistikkRepository,
    private val clock: Clock = systemDefaultZone()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        fun konstruer(
            gatewayProvider: GatewayProvider,
            repositoryProvider: RepositoryProvider,
        ): SaksStatistikkService {
            return SaksStatistikkService(
                behandlingService = BehandlingService(repositoryProvider.provide()),
                rettighetstypeperiodeRepository = repositoryProvider.provide(),
                bigQueryKvitteringRepository = repositoryProvider.provide(),
                skjermingService = SkjermingService.konstruer(gatewayProvider),
                sakstatistikkRepository = repositoryProvider.provide(),
                oppgaveHendelseRepository = repositoryProvider.provide(),
            )
        }
    }

    fun lagreSakInfoTilBigquery(behandlingId: BehandlingId) {
        val behandling = behandlingService.hentBehandling(behandlingId)
        require(
            behandling.typeBehandling in Konstanter.interessanteBehandlingstyper
        ) {
            "Denne jobben skal ikke kunne bli trigget av oppfølgingsbehandlinger. Behandling: ${behandling.referanse}"
        }

        val erSkjermet = skjermingService.erSkjermet(behandling)

        val sekvensNummer = bigQueryKvitteringRepository.lagreKvitteringForSak(behandling)

        val bqSak = bqBehandlingForBehandling(behandling, erSkjermet, sekvensNummer)

        lagreBQBehandling(bqSak)
    }

    fun lagreBQBehandling(bqSak: BQBehandling) {
        val siste = sakstatistikkRepository.hentSisteHendelseForBehandling(bqSak.behandlingUUID)

        if (siste == null || siste.ansesSomDuplikat(bqSak) != true) {
            // Hvis vi ikke allerede har en inngangshendelse (når behandlingen ble
            // opprettet), konstruer en.
            // Dette er kun et teknisk krav fra Team Sak om at alle hendelser bør ha inngangshendelser.
            // I praksis vil de "normale" hendelsene kun komme et par sekunder etter at behandlingen
            // ble opprettet i behandlingsflyt.
            if (!erInngangsHendelse(bqSak) && siste == null) {
                sakstatistikkRepository.lagre(
                    bqSak.copy(
                        endretTid = bqSak.registrertTid,
                        ferdigbehandletTid = null,
                        vedtakTid = null,
                        behandlingStatus = "OPPRETTET"
                    )
                )
                PrometheusProvider.prometheus.sakDuplikat(false).increment()
            }
            sakstatistikkRepository.lagre(bqSak)
            PrometheusProvider.prometheus.sakDuplikat(false).increment()
            if (siste != null) {
                if (!Miljø.erProd()) {
                    log.info("Ny hendelse med samme endretTid. Forrige: $siste. Ny: $bqSak.")
                }
            }
        } else {
            log.info("Lagret ikke sakstatistikk for behandling ${bqSak.behandlingUUID} siden den anses som duplikat.")
            PrometheusProvider.prometheus.sakDuplikat(true).increment()
        }
    }

    private fun erInngangsHendelse(bqBehandling: BQBehandling): Boolean {
        return nærNokITid(bqBehandling.registrertTid, bqBehandling.endretTid)
    }

    private fun bqBehandlingForBehandling(
        behandling: Behandling,
        erSkjermet: Boolean,
        sekvensNummer: Long?
    ): BQBehandling {
        val sak = behandling.sak
        val relatertBehandlingUUID = behandlingService.hentRelatertBehandlingUUID(behandling)
        val hendelser = behandling.hendelser
        val sisteHendelse = hendelser.last()
        val behandlingReferanse = behandling.referanse

        val ansvarligEnhet = ansvarligEnhet(behandlingReferanse, behandling, erSkjermet)
        val saksbehandlerIdent = sisteHendelse.saksbehandler?.ident

        if (saksbehandlerIdent == null) {
            log.info("Fant ikke siste saksbehandler for behandling $behandlingReferanse. Avklaringsbehov: ${sisteHendelse.avklaringsBehov}.")
        }

        val årsakTilOpprettelse = behandling.årsakTilOpprettelse
        if (årsakTilOpprettelse == null) {
            log.info("Årsak til opprettelse er ikke satt. Behandling: $behandlingReferanse. Sak: ${sak.saksnummer}.")
            PrometheusProvider.prometheus.årsakTilOpprettelseIkkeSatt().increment()
        }

        val saksbehandler =
            if (erSkjermet) "-5" else saksbehandlerIdent

        if (behandling.mottattTid.isAfter(behandling.opprettetTid)) {
            log.info("Mottatt-tid er større enn opprettet-tid. Behandling: $behandlingReferanse. Mottatt: ${behandling.mottattTid}, opprettet: ${behandling.opprettetTid}.")
        }

        return BQBehandling(
            sekvensNummer = sekvensNummer,
            behandlingUUID = behandlingReferanse,
            relatertBehandlingUUID = relatertBehandlingUUID,
            relatertFagsystem = if (relatertBehandlingUUID != null) "Kelvin" else null,
            ferdigbehandletTid = hendelser.ferdigBehandletTid(),
            behandlingType = behandling.typeBehandling.toString().uppercase(),
            aktorId = sak.person.ident,
            saksnummer = sak.saksnummer.value,
            tekniskTid = LocalDateTime.now(clock),
            registrertTid = behandling.opprettetTid.truncatedTo(ChronoUnit.SECONDS),
            endretTid = behandling.oppdatertTidspunkt(),
            versjon = sisteHendelse.versjon.verdi,
            mottattTid = behandling.mottattTid.truncatedTo(ChronoUnit.SECONDS),
            opprettetAv = behandling.opprettetAv ?: KELVIN,
            ansvarligBeslutter = if (erSkjermet && sisteHendelse.ansvarligBeslutter != null) "-5" else sisteHendelse.ansvarligBeslutter,
            vedtakTid = sisteHendelse.vedtakstidspunkt,
            søknadsFormat = behandling.søknadsformat,
            saksbehandler = saksbehandler,
            behandlingMetode = behandling.behandlingMetode().also {
                if (it == BehandlingMetode.AUTOMATISK) log.info(
                    "Behandling $behandlingReferanse er automatisk behandlet. Behandling ${behandling.referanse}"
                )
            },
            behandlingStatus = behandlingStatus(behandling, sisteHendelse),
            behandlingÅrsak = årsakTilOpprettelse ?: behandling.årsaker.prioriterÅrsaker().name,
            behandlingResultat = regnUtBehandlingResultat(behandling),
            resultatBegrunnelse = resultatBegrunnelse(hendelser),
            ansvarligEnhetKode = ansvarligEnhet,
            sakYtelse = "AAP",
            erResending = false
        )
    }

    fun alleHendelserPåBehandling(
        behandlingId: BehandlingId
    ): List<BQBehandling> {
        val behandling = behandlingService.hentBehandling(behandlingId)

        val erSkjermet = skjermingService.erSkjermet(behandling)

        val originaleHendelser =
            sakstatistikkRepository.hentAlleHendelserPåBehandling(behandling.referanse)

        val alleHendelser =
            (1..<behandling.hendelser.size + 1)
                .map { behandling.hendelser.subList(0, it) }
                .map { hendelser ->
                    bqBehandlingForBehandling(
                        behandling = behandling.copy(hendelser = hendelser),
                        erSkjermet = erSkjermet,
                        sekvensNummer = null,
                    )
                }

        return flettBehandlingHendelser(originaleHendelser, alleHendelser)
    }

    /**
     * Må bevare mengden av endringTid-verdier pga krav fra saksstatistikk.
     */
    private fun flettBehandlingHendelser(
        originaleHendelser: List<BQBehandling>,
        nyeHendelser: List<BQBehandling>
    ): List<BQBehandling> {
        val sorterteNye = nyeHendelser.sortedBy { it.endretTid }
        val nyeQueue = LinkedList(sorterteNye)

        var forrigeNye: BQBehandling? = null
        val originaleMedNyData = originaleHendelser
            .sorted()
            .map {
                if (nyeQueue.isEmpty()) {
                    val last = sorterteNye.last()
                    forrigeNye = last
                    last.copy(endretTid = it.endretTid) // ???
                } else if (nærNokITid(it.endretTid, nyeQueue.peek().endretTid)) {
                    val ny = nyeQueue.poll()
                    forrigeNye = ny
                    ny.copy(endretTid = it.endretTid)
                } else if (it.endretTid.isBefore(nyeQueue.peek().endretTid) && forrigeNye != null) {
                    /*
                    GAMMEL: -------   X ---------
                    NY    : -- Z ------- Y ------ (Z = forrigeNye)
                     */
                    forrigeNye.copy(endretTid = it.endretTid)
                } else if (it.endretTid.isBefore(nyeQueue.peek().endretTid) && forrigeNye == null) {
                    /*
                    GAMMEL: ---- X ---------
                    NY    : ---------- Y ---
                     */
                    val ny = nyeQueue.poll()
                    forrigeNye = ny
                    ny.copy(endretTid = it.endretTid)
                } else if (it.endretTid.isAfter(nyeQueue.peek().endretTid) && forrigeNye != null) {
                    /*
                    GAMMEL: ------------ X --
                    NY    : -- Z --- Y ------
                     */
                    val ny = nyeQueue.poll()
                    forrigeNye = ny
                    ny.copy(endretTid = it.endretTid)
                } else if (it.endretTid.isAfter(nyeQueue.peek().endretTid) && forrigeNye == null) {
                    /*
                    GAMMEL: ------------ X --
                    NY    : ------ Y ------
                     */
                    val ny = nyeQueue.poll()
                    forrigeNye = ny
                    ny.copy(endretTid = it.endretTid)
                } else {
                    error("Ukjent situasjon")
                }
            }

        check(originaleMedNyData.map { it.endretTid }
            .toSet() == originaleHendelser.map { it.endretTid }.toSet())

        val nyeTidspunkter = nyeHendelser.map { it.endretTid }.toSet()
            .minus(originaleMedNyData.map { it.endretTid }.toSet())
        val gamleTidspunkter = originaleHendelser.map { it.endretTid }.toSet()
            .minus(nyeHendelser.map { it.endretTid }.toSet())
        log.info("Nye tidspunkter: $nyeTidspunkter. Gamle tidspunkter: $gamleTidspunkter.")

        return (nyeQueue.toList() + originaleMedNyData).sorted()
            .fold(listOf()) { acc, curr ->
                val forrige = acc.lastOrNull()
                if (forrige == null) {
                    acc + curr
                } else {
                    if (forrige.ansesSomDuplikat(curr)) {
                        acc
                    } else {
                        acc + curr
                    }
                }
            }
    }

    private fun nærNokITid(
        originalTid: LocalDateTime,
        nyTid: LocalDateTime
    ): Boolean {
        val duration = Duration.between(originalTid, nyTid)
        return duration.abs().toMillis() <= 10
    }

    /**
     * Om behandlingen er returnert fra kvalitetssikrer, skal dette være returårsaken.
     *
     * TODO: begrunnelse ved avslag/innvilgelse
     */
    private fun resultatBegrunnelse(
        behandlingHendelser: List<BehandlingHendelse>
    ): String? {
        if (behandlingHendelser.last().avklaringsbehovStatus?.returnert() == true) {
            return behandlingHendelser.last().returÅrsak
        }
        return null
    }

    /**
     * For en avsluttet behandling, tolkes dette som rettighetstype.
     */
    private fun regnUtBehandlingResultat(
        behandling: Behandling
    ): String? {
        return when (behandling.behandlingStatus()) {
            BehandlingStatus.OPPRETTET,
            BehandlingStatus.UTREDES -> null

            BehandlingStatus.IVERKSETTES,
            BehandlingStatus.AVSLUTTET -> {
                val behandlingReferanse = behandling.referanse
                val rettighetstyper = rettighetstypeperiodeRepository.hent(behandlingReferanse)
                val resultat = behandling.resultat()

                when (resultat) {
                    ResultatKode.INNVILGET -> if (rettighetstyper.isEmpty()) {
                        // Hva med avslag?
                        "AAP"
                    } else {
                        if (rettighetstyper.size > 1) {
                            log.info("Mer enn én rettighetstype for behandling $behandlingReferanse. Velger første.")
                        }
                        // TODO: her må vi sikkert heller klippe på dato. Denne vil jo vokse over tid?
                        val førsteRettighetstype =
                            rettighetstyper.first().rettighetstype.name.lowercase()
                        "AAP_$førsteRettighetstype"
                    }

                    ResultatKode.AVSLAG -> "AVSLAG"
                    ResultatKode.TRUKKET -> "TRUKKET"
                    ResultatKode.KLAGE_OPPRETTHOLDES -> "KLAGE_OPPRETTHOLDES"
                    ResultatKode.KLAGE_OMGJØRES -> "KLAGE_OMGJØRES"
                    ResultatKode.KLAGE_DELVIS_OMGJØRES -> "KLAGE_DELVIS_OMGJØRES"
                    ResultatKode.KLAGE_AVSLÅTT -> "KLAGE_AVSLÅTT"
                    ResultatKode.KLAGE_TRUKKET -> "KLAGE_TRUKKET"
                    ResultatKode.AVBRUTT -> "AVBRUTT"
                    null -> "UDEFINERT"
                }
            }
        }
    }

    private fun ansvarligEnhet(
        behandlingReferanse: UUID,
        behandling: Behandling,
        erSkjermet: Boolean,
    ): String? {
        val sisteHendelse = behandling.hendelser.last()
        val sisteHendelsevklaringsbehov = sisteHendelse.avklaringsBehov
        val enhet = sisteHendelsevklaringsbehov?.let {
            oppgaveHendelseRepository.hentEnhetForAvklaringsbehov(
                behandlingReferanse,
                it
            )
        }?.lastOrNull {
            // I tilfelle enhet har flyttet seg på samme avklaringsbehov
            it.tidspunkt.isBefore(
                sisteHendelse.hendelsesTidspunkt.plusDays(1) // STYGT
            )
        }?.enhet

        if (enhet == null) {
            log.info("Fant ikke enhet for behandling $behandlingReferanse. Avklaringsbehov: $sisteHendelsevklaringsbehov. Typebehandling: ${behandling.typeBehandling}. Årsak til opprettelse: ${behandling.årsakTilOpprettelse}")
            val fallbackEnhet =
                oppgaveHendelseRepository.hentSisteEnhetPåBehandling(behandlingReferanse)

            if (fallbackEnhet != null) {
                val (enhetOgTidspunkt, avklaringsBehov) = fallbackEnhet
                val fallbackEnhet = enhetOgTidspunkt.enhet
                log.info("Fallback-enhet: $fallbackEnhet for avklaringsbehov ${avklaringsBehov}. Originalt behov: $sisteHendelsevklaringsbehov. Referanse: $behandlingReferanse. Typebehandling: ${behandling.typeBehandling}. Årsak til opprettelse: ${behandling.årsakTilOpprettelse}")
                return fallbackEnhet
            } else {
                log.info("Fant ingen enhet eller fallbackenhet. Referanse: $behandlingReferanse. Avklaringsbehov: $sisteHendelsevklaringsbehov. Typebehandling: ${behandling.typeBehandling}. Årsak til opprettelse: ${behandling.årsakTilOpprettelse}.")
            }
        }
        if (erSkjermet) {
            return "-5"
        }
        return enhet
    }

    private fun behandlingStatus(behandling: Behandling, hendelse: BehandlingHendelse): String {
        val venteÅrsak = hendelse.venteÅrsak?.let { "_${it.uppercase()}" }.orEmpty()
        val returStatus = hendelse.avklaringsbehovStatus
            ?.takeIf { it.returnert() }
            ?.let { "_${it.name.uppercase()}" }.orEmpty()

        return when (hendelse.status) {
            BehandlingStatus.OPPRETTET -> "OPPRETTET"
            BehandlingStatus.UTREDES -> "UNDER_BEHANDLING$venteÅrsak$returStatus"
            BehandlingStatus.IVERKSETTES -> "IVERKSETTES"
            BehandlingStatus.AVSLUTTET -> {
                if (behandling.typeBehandling == TypeBehandling.Klage && behandling.resultat()
                        ?.sendesTilKA() == true
                ) {
                    "OVERSENDT_KA"
                } else {
                    "AVSLUTTET"
                }
            }
        }
    }

    fun List<BehandlingHendelse>.ferdigBehandletTid(): LocalDateTime? {
        return this.firstOrNull { it.status == BehandlingStatus.AVSLUTTET }?.hendelsesTidspunkt
    }
}