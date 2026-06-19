package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.sakDuplikat
import no.nav.aap.statistikk.sammeEndretTid
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class SaksStatistikkService(
    private val behandlingService: BehandlingService,
    private val sakstatistikkRepository: SakstatistikkRepository,
    private val bqBehandlingMapper: BQBehandlingMapper,
) : ISaksStatistikkService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun lagreSakInfoTilBigquery(
        behandlingId: BehandlingId,
        cutoffTidspunkt: LocalDateTime?
    ): SakStatistikkResultat {
        val behandling = behandlingService.hentBehandling(behandlingId)
        val behandlingPåTidspunkt = cutoffTidspunkt?.let { behandling.påTidspunkt(it) } ?: behandling
        require(
            behandlingPåTidspunkt.typeBehandling in Konstanter.interessanteBehandlingstyper
        ) {
            "Denne jobben skal ikke kunne bli trigget av oppfølgingsbehandlinger. Behandling: ${behandlingPåTidspunkt.referanse}"
        }

        val erSkjermet = behandlingService.erSkjermet(behandlingPåTidspunkt)

        val bqSak =
            bqBehandlingMapper.bqBehandlingForBehandling(behandlingPåTidspunkt, erSkjermet)

        log.info(
            "lagreSakInfoTilBigquery: endretTid=${bqSak.endretTid}, " +
                    "behandling.oppdatertTidspunkt=${behandlingPåTidspunkt.oppdatertTidspunkt()}, " +
                    "oppgaveDrivenEndretTid=${bqSak.endretTid.isAfter(behandlingPåTidspunkt.oppdatertTidspunkt())}, " +
                    "status=${bqSak.behandlingStatus}."
        )

        val manglerEnhet =
            bqSak.ansvarligEnhetKode == null && bqSak.behandlingMetode != BehandlingMetode.AUTOMATISK

        if (manglerEnhet) {
            return SakStatistikkResultat.ManglerEnhet(
                behandlingId = behandlingPåTidspunkt.id(),
                avklaringsbehovKode = behandlingPåTidspunkt.gjeldendeAvklaringsBehov,
            )
        }

        lagreBQBehandling(bqSak)

        return SakStatistikkResultat.OK
    }

    fun lagreBQBehandling(nyBQBehandling: BQBehandling) {
        sakstatistikkRepository.acquireBehandlingLock(nyBQBehandling.behandlingUUID)
        val eksisterendeRad = sakstatistikkRepository.hentSisteHendelseForBehandling(nyBQBehandling.behandlingUUID)

        if (eksisterendeRad?.ansesSomDuplikat(nyBQBehandling) != true) {
            // Hvis vi ikke allerede har en inngangshendelse (når behandlingen ble
            // opprettet), konstruer en.
            // Dette er kun et teknisk krav fra Team Sak om at alle hendelser bør ha inngangshendelser.
            // I praksis vil de "normale" hendelsene kun komme et par sekunder etter at behandlingen
            // ble opprettet i behandlingsflyt.
            if (!erInngangsHendelse(nyBQBehandling) && eksisterendeRad == null) {
                sakstatistikkRepository.lagre(
                    nyBQBehandling.copy(
                        endretTid = nyBQBehandling.registrertTid,
                        ferdigbehandletTid = null,
                        vedtakTid = null,
                        behandlingStatus = "OPPRETTET"
                    )
                )
                PrometheusProvider.prometheus.sakDuplikat(false).increment()
            }

            val bqSakMedUnikEndretTid = tilpassEndretTid(nyBQBehandling, eksisterendeRad) ?: run {
                PrometheusProvider.prometheus.sakDuplikat(true).increment()
                return
            }

            sakstatistikkRepository.lagre(bqSakMedUnikEndretTid)
            if (eksisterendeRad?.behandlingStatus == "AVSLUTTET"
                && bqSakMedUnikEndretTid.behandlingStatus != "AVSLUTTET"
                && bqSakMedUnikEndretTid.endretTid > eksisterendeRad.endretTid
            ) {
                log.error(
                    "Feil rekkefølge: lagrer ${bqSakMedUnikEndretTid.behandlingStatus} etter at AVSLUTTET allerede er lagret. " +
                            "Referanse: ${bqSakMedUnikEndretTid.behandlingUUID}. " +
                            "Forrige endretTid: ${eksisterendeRad.endretTid}, ny endretTid: ${bqSakMedUnikEndretTid.endretTid}."
                )
            }
            PrometheusProvider.prometheus.sakDuplikat(false).increment()
        } else {
            log.info("Lagret ikke sakstatistikk for behandling ${nyBQBehandling.behandlingUUID} siden den anses som duplikat.")
            PrometheusProvider.prometheus.sakDuplikat(true).increment()
        }
    }

    /**
     * Justerer endretTid for å sikre korrekt rekkefølge og idempotens:
     * - Samme endretTid som siste: bumpes med +1µs (to samtidige hendelser)
     * - Eldre endretTid enn siste: lagres med opprinnelig tidsstempel på riktig historisk posisjon;
     *   returnerer null dersom hendelsen allerede er lagret (idempotens ved gjentagende retries)
     * - Nyere endretTid enn siste: returneres uendret
     */
    private fun tilpassEndretTid(nyRad: BQBehandling, eksisterendeRad: BQBehandling?): BQBehandling? = when {
        eksisterendeRad == null -> nyRad
        eksisterendeRad.endretTid == nyRad.endretTid -> håndterSammeEndretTid(eksisterendeRad, nyRad)
        eksisterendeRad.endretTid > nyRad.endretTid -> {
            val eksisterende = sakstatistikkRepository.hentHendelseMedEndretTid(
                nyRad.behandlingUUID, nyRad.endretTid, nyRad.erResending
            )
            if (eksisterende?.ansesSomDuplikat(nyRad) == true) {
                log.info(
                    "Forsinket hendelse allerede lagret med samme endretTid — hopper over. " +
                            "Referanse: ${nyRad.behandlingUUID}. endretTid: ${nyRad.endretTid}."
                )
                null
            } else {
                log.info(
                    "Ny hendelse har eldre endretTid enn forrige lagrede rad — lagrer med opprinnelig tidsstempel. " +
                            "Referanse: ${nyRad.behandlingUUID}. " +
                            "Forrige endretTid: ${eksisterendeRad.endretTid}, ny: ${nyRad.endretTid}. " +
                            "Forrige status: ${eksisterendeRad.behandlingStatus}, ny status: ${nyRad.behandlingStatus}."
                )
                nyRad
            }
        }
        else -> nyRad
    }

    private fun erInngangsHendelse(bqBehandling: BQBehandling): Boolean {
        return nærNokITid(bqBehandling.registrertTid, bqBehandling.endretTid)
    }

    fun alleHendelserPåBehandling(
        behandlingId: BehandlingId
    ): List<BQBehandling> {
        val behandling = behandlingService.hentBehandling(behandlingId)

        val erSkjermet = behandlingService.erSkjermet(behandling)

        val originaleHendelser =
            sakstatistikkRepository.hentAlleHendelserPåBehandling(behandling.referanse)

        val alleHendelser = behandling.hendelsesHistorikk()
            .map { behandling ->
                bqBehandlingMapper.bqBehandlingForBehandling(
                    behandling = behandling,
                    erSkjermet = erSkjermet,
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

    private fun håndterSammeEndretTid(siste: BQBehandling, bqSak: BQBehandling): BQBehandling? {
        return when {
            siste.behandlingStatus == "AVSLUTTET" && bqSak.behandlingStatus != "AVSLUTTET" && !bqSak.erResending ->
                håndterAvsluttetMedNyStatus(siste, bqSak)

            siste.behandlingStatus == "AVSLUTTET" && bqSak.behandlingStatus != "AVSLUTTET" && bqSak.erResending ->
                håndterResendingEtterAvsluttet(bqSak)

            else -> håndterAndreHendelser(siste, bqSak)
        }
    }

    private fun håndterAvsluttetMedNyStatus(siste: BQBehandling, bqSak: BQBehandling): BQBehandling? {
        log.warn(
            "Hopper over hendelse med samme endretTid fordi AVSLUTTET allerede er lagret. " +
                    "Referanse: ${bqSak.behandlingUUID}. " +
                    "Eksisterende status: ${siste.behandlingStatus}, ny status: ${bqSak.behandlingStatus}, " +
                    "endretTid: ${bqSak.endretTid}."
        )
        return null
    }

    private fun håndterResendingEtterAvsluttet(bqSak: BQBehandling): BQBehandling? {
        val eksisterende = sakstatistikkRepository.hentHendelseMedEndretTid(
            bqSak.behandlingUUID, bqSak.endretTid, bqSak.erResending
        )
        return if (eksisterende?.ansesSomDuplikat(bqSak) == true) {
            log.info(
                "Forsinket resending-hendelse allerede lagret som duplikat — hopper over. " +
                        "Referanse: ${bqSak.behandlingUUID}. endretTid: ${bqSak.endretTid}."
            )
            null
        } else {
            log.info(
                "Lagrer resending-hendelse med samme endretTid som AVSLUTTET (ikke duplikat). " +
                        "Referanse: ${bqSak.behandlingUUID}. " +
                        "Status: ${bqSak.behandlingStatus}, endretTid: ${bqSak.endretTid}."
            )
            bqSak
        }
    }

    private fun håndterAndreHendelser(siste: BQBehandling, bqSak: BQBehandling): BQBehandling {
        if (siste.behandlingStatus == "AVSLUTTET" && bqSak.behandlingStatus == "AVSLUTTET") {
            log.warn(
                "Ny AVSLUTTET-hendelse med samme endretTid lagres med bump for å bevare rekkefølge. " +
                        "Referanse: ${bqSak.behandlingUUID}. EndretTid: ${bqSak.endretTid}. " +
                        "Forrige saksbehandler: ${siste.saksbehandler}, ny: ${bqSak.saksbehandler}. " +
                        "Forrige enhet: ${siste.ansvarligEnhetKode}, ny: ${bqSak.ansvarligEnhetKode}."
            )
        } else {
            log.info(
                "Ny hendelse med samme endretTid. Forrige teknisk tid: ${siste.tekniskTid}. " +
                        "Ny: ${bqSak.tekniskTid}. Referanse: ${bqSak.behandlingUUID}. " +
                        "EndretTid: ${bqSak.endretTid}. " +
                        "Forrige status: ${siste.behandlingStatus}, ny status: ${bqSak.behandlingStatus}. " +
                        "Forrige saksbehandler: ${siste.saksbehandler}, ny: ${bqSak.saksbehandler}. " +
                        "Forrige enhet: ${siste.ansvarligEnhetKode}, ny: ${bqSak.ansvarligEnhetKode}."
            )
        }
        PrometheusProvider.prometheus.sammeEndretTid().increment()
        return bqSak.copy(endretTid = siste.endretTid.plusNanos(1000))
    }

    private fun nærNokITid(
        originalTid: LocalDateTime,
        nyTid: LocalDateTime
    ): Boolean {
        val duration = Duration.between(originalTid, nyTid)
        return duration.abs().toMillis() <= 10
    }
}