package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.avsluttetbehandling.IRettighetstypeperiodeRepository
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.bigquery.IBQSakstatistikkRepository
import no.nav.aap.statistikk.hendelser.ferdigBehandletTid
import no.nav.aap.statistikk.hendelser.returnert
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepository
import no.nav.aap.statistikk.sak.IBigQueryKvitteringRepository
import no.nav.aap.statistikk.skjerming.SkjermingService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Clock.systemDefaultZone
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class SaksStatistikkService(
    private val behandlingRepository: IBehandlingRepository,
    private val rettighetstypeperiodeRepository: IRettighetstypeperiodeRepository,
    private val bigQueryKvitteringRepository: IBigQueryKvitteringRepository,
    private val bigQueryRepository: IBQSakstatistikkRepository,
    private val skjermingService: SkjermingService,
    private val oppgaveHendelseRepository: OppgaveHendelseRepository,
    private val sakstatistikkRepository: SakstatistikkRepository,
    private val clock: Clock = systemDefaultZone()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        fun konstruer(
            bigQueryRepository: IBQSakstatistikkRepository,
            gatewayProvider: GatewayProvider,
            repositoryProvider: RepositoryProvider,
        ): SaksStatistikkService {
            return SaksStatistikkService(
                behandlingRepository = repositoryProvider.provide(),
                rettighetstypeperiodeRepository = repositoryProvider.provide(),
                bigQueryKvitteringRepository = repositoryProvider.provide(),
                bigQueryRepository = bigQueryRepository,
                skjermingService = SkjermingService.konstruer(gatewayProvider),
                sakstatistikkRepository = repositoryProvider.provide(),
                oppgaveHendelseRepository = repositoryProvider.provide(),
            )
        }
    }

    fun lagreSakInfoTilBigquery(behandlingId: BehandlingId) {
        val behandling = behandlingRepository.hent(behandlingId)
        require(behandling.typeBehandling !in listOf(TypeBehandling.Oppfølgingsbehandling)) {
            "Denne jobben skal ikke kunne bli trigget av oppfølgingsbehandlinger. Behandling: ${behandling.referanse}"
        }

        val erSkjermet = skjermingService.erSkjermet(behandling)

        val sekvensNummer = bigQueryKvitteringRepository.lagreKvitteringForSak(behandling)

        val bqSak = bqBehandlingForBehandling(behandling, erSkjermet, sekvensNummer)

        // TODO - kun lagre om endring siden sist
        bigQueryRepository.lagre(bqSak)

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
                        ferdigbehandletTid = null, vedtakTid = null, behandlingStatus = "OPPRETTET"
                    )
                )
            }
            sakstatistikkRepository.lagre(bqSak)
            if (siste != null && siste.endretTid.truncatedTo(ChronoUnit.SECONDS) == bqSak.endretTid.truncatedTo(
                    ChronoUnit.SECONDS
                )
            ) {
                if (!Miljø.erProd()) {
                    log.info("Ny hendelse med samme endretTid. Forrige: $siste. Ny: $bqSak.")
                }
            }
        } else {
            log.info("Lagret ikke sakstatistikk for behandling ${bqSak.behandlingUUID} siden den anses som duplikat.")
        }
    }

    private fun erInngangsHendelse(bqBehandling: BQBehandling): Boolean {
        return nærNokITid(bqBehandling.registrertTid, bqBehandling.endretTid)
    }

    private fun hentRelatertBehandlingUUID(behandling: Behandling): UUID? =
        behandling.relatertBehandlingId?.let { behandlingRepository.hent(it) }?.referanse

    fun bqBehandlingForBehandling(
        behandling: Behandling,
        erSkjermet: Boolean,
        sekvensNummer: Long?
    ): BQBehandling {
        val sak = behandling.sak
        val relatertBehandlingUUID = hentRelatertBehandlingUUID(behandling)
        val hendelser = behandling.hendelser
        val sisteHendelse = hendelser.last()
        val behandlingReferanse = behandling.referanse

        val enhet = sisteHendelse.avklaringsBehov?.let {
            oppgaveHendelseRepository.hentEnhetForAvklaringsbehov(behandlingReferanse, it)
        }?.lastOrNull {
            it.tidspunkt.isBefore(
                sisteHendelse.hendelsesTidspunkt.plusDays(1) // STYGT
            )
        }?.enhet
        val ansvarligEnhet = ansvarligEnhet(enhet, erSkjermet)

        if (ansvarligEnhet == null) {
            log.info("Fant ikke enhet for behandling $behandlingReferanse. Avklaringsbehov: ${sisteHendelse.avklaringsBehov}.")
        }

        val saksbehandlerIdent = sisteHendelse.saksbehandler?.ident

        if (saksbehandlerIdent == null) {
            log.info("Fant ikke siste saksbehandler for behandling $behandlingReferanse. Avklaringsbehov: ${sisteHendelse.avklaringsBehov}.")
        }

        val saksbehandler =
            if (erSkjermet) "-5" else saksbehandlerIdent

        if (behandling.årsaker.size > 1) {
            log.info("Behandling med referanse $behandlingReferanse hadde mer enn én årsak. Avgir den første.")
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
                    "Behandling $behandlingReferanse er automatisk behandlet. Behandling $behandling"
                )
            },
            behandlingStatus = behandlingStatus(sisteHendelse),
            behandlingÅrsak = behandling.årsaker.prioriterÅrsaker().name,
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
        val behandling = behandlingRepository.hent(behandlingId)

        val erSkjermet = skjermingService.erSkjermet(behandling)

        val originaleHendelser =
            sakstatistikkRepository.hentAlleHendelserPåBehandling(behandling.referanse)

        val alleHendelser =
            (1..<behandling.hendelser.size + 1).map { behandling.hendelser.subList(0, it) }
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
    fun flettBehandlingHendelser(
        originaleHendelser: List<BQBehandling>,
        nyeHendelser: List<BQBehandling>
    ): List<BQBehandling> {
        val sorterteNye = nyeHendelser.sortedBy { it.endretTid }
        val nyeQueue = LinkedList(sorterteNye)

        var forrigeNye: BQBehandling? = null
        val originaleMedNyData = originaleHendelser
            .sortedBy { it.endretTid }
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

        return (nyeQueue.toList() + originaleMedNyData).sortedBy { it.endretTid }
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
        return if (duration.abs().toMillis() <= 10) {
            true
        } else {
            false
        }
    }

    fun leggTilHvisIkkeDuplikat(
        forrige: BQBehandling?,
        kandidat: BQBehandling,
        nyListe: MutableList<BQBehandling>
    ): Boolean {
        if ((forrige != null && !forrige.ansesSomDuplikat(kandidat)) || forrige == null) {
            nyListe.add(kandidat)
            return true
        }
        return false
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
        return when (behandling.status) {
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
                    null -> null
                }
            }
        }
    }

    private fun ansvarligEnhet(
        enhet: String?,
        erSkjermet: Boolean,
    ): String? {
        if (erSkjermet) {
            return "-5"
        }
        return enhet
    }

    fun behandlingStatus(hendelse: BehandlingHendelse): String {
        // TODO: når klage er implementert, må dette fikses her

        val venteÅrsak = hendelse.venteÅrsak?.let { "_${it.uppercase()}" }.orEmpty()
        val returStatus = hendelse.avklaringsbehovStatus
            ?.takeIf { it.returnert() }
            ?.let { "_${it.name.uppercase()}" }.orEmpty()


        return when (hendelse.status) {
            BehandlingStatus.OPPRETTET -> "REGISTRERT"
            BehandlingStatus.UTREDES -> "UNDER_BEHANDLING$venteÅrsak$returStatus"
            BehandlingStatus.IVERKSETTES -> "IVERKSETTES"
            BehandlingStatus.AVSLUTTET -> "AVSLUTTET"
        }
    }

}