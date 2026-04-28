package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.sakDuplikat
import no.nav.aap.statistikk.sammeEndretTid
import org.slf4j.LoggerFactory
import java.time.Clock
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
        lagreUtenEnhet: Boolean
    ): SakStatistikkResultat {
        val behandling = behandlingService.hentBehandling(behandlingId)
        require(
            behandling.typeBehandling in Konstanter.interessanteBehandlingstyper
        ) {
            "Denne jobben skal ikke kunne bli trigget av oppfølgingsbehandlinger. Behandling: ${behandling.referanse}"
        }

        val erSkjermet = behandlingService.erSkjermet(behandling)

        val bqSak =
            bqBehandlingMapper.bqBehandlingForBehandling(behandling, erSkjermet)

        log.info(
            "lagreSakInfoTilBigquery: endretTid=${bqSak.endretTid}, " +
                    "behandling.oppdatertTidspunkt=${behandling.oppdatertTidspunkt()}, " +
                    "oppgaveDrivenEndretTid=${bqSak.endretTid.isAfter(behandling.oppdatertTidspunkt())}, " +
                    "status=${bqSak.behandlingStatus}."
        )

        val manglerEnhet = !lagreUtenEnhet &&
                bqSak.ansvarligEnhetKode == null && bqSak.behandlingMetode != BehandlingMetode.AUTOMATISK

        if (manglerEnhet) {
            return SakStatistikkResultat.ManglerEnhet(
                behandlingId = behandling.id(),
                avklaringsbehovKode = behandling.gjeldendeAvklaringsBehov,
                bqBehandling = bqSak,
            )
        }

        val manglerFortsattEnhet =
            bqSak.ansvarligEnhetKode == null && bqSak.behandlingMetode != BehandlingMetode.AUTOMATISK
        if (manglerFortsattEnhet) {
            val referanse = behandling.referanse
            val saksnummer = behandling.sak.saksnummer
            log.warn("Ansvarlig enhet er ikke satt. Behandling: $referanse. Sak: $saksnummer. Status: ${behandling.behandlingStatus()}. Årsak: ${behandling.årsakTilOpprettelse}.")
            log.warn("Saksbehandler er ikke satt. Behandling: $referanse. Sak: $saksnummer. Status: ${behandling.behandlingStatus()}. Årsak: ${behandling.årsakTilOpprettelse}.")
        }

        lagreBQBehandling(bqSak)

        return SakStatistikkResultat.OK
    }

    override fun lagreMedStoredBQBehandling(
        behandlingId: BehandlingId,
        storedBQBehandling: BQBehandling,
        avklaringsbehovKode: Definisjon?,
    ): SakStatistikkResultat {
        val behandling = behandlingService.hentBehandling(behandlingId)
        val erSkjermet = behandlingService.erSkjermet(behandling)
        val (enhet, saksbehandler) = bqBehandlingMapper.hentEnhetOgSaksbehandler(
            behandling, erSkjermet, avklaringsbehovKode
        )
        if (enhet == null) {
            return SakStatistikkResultat.ManglerEnhet(
                behandlingId = behandlingId,
                avklaringsbehovKode = avklaringsbehovKode,
                bqBehandling = storedBQBehandling,
            )
        }
        lagreBQBehandling(storedBQBehandling.copy(ansvarligEnhetKode = enhet, saksbehandler = saksbehandler))
        return SakStatistikkResultat.OK
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

            val bqSakMedUnikEndretTid = tilpassEndretTid(bqSak, siste) ?: run {
                PrometheusProvider.prometheus.sakDuplikat(true).increment()
                return
            }

            sakstatistikkRepository.lagre(bqSakMedUnikEndretTid)
            if (siste?.behandlingStatus == "AVSLUTTET" && bqSakMedUnikEndretTid.behandlingStatus == "IVERKSETTES") {
                log.error(
                    "Feil rekkefølge: lagrer IVERKSETTES etter at AVSLUTTET allerede er lagret. " +
                            "Referanse: ${bqSakMedUnikEndretTid.behandlingUUID}. " +
                            "Forrige endretTid: ${siste.endretTid}, ny endretTid: ${bqSakMedUnikEndretTid.endretTid}."
                )
            }
            PrometheusProvider.prometheus.sakDuplikat(false).increment()
        } else {
            log.info("Lagret ikke sakstatistikk for behandling ${bqSak.behandlingUUID} siden den anses som duplikat.")
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
    private fun tilpassEndretTid(bqSak: BQBehandling, siste: BQBehandling?): BQBehandling? = when {
        siste == null -> bqSak
        siste.endretTid == bqSak.endretTid -> {
            log.info(
                "Ny hendelse med samme endretTid. Forrige teknisk tid: ${siste.tekniskTid}. " +
                        "Ny: ${bqSak.tekniskTid}. Referanse: ${bqSak.behandlingUUID}. " +
                        "EndretTid: ${bqSak.endretTid}. " +
                        "Forrige status: ${siste.behandlingStatus}, ny status: ${bqSak.behandlingStatus}. " +
                        "Forrige saksbehandler: ${siste.saksbehandler}, ny: ${bqSak.saksbehandler}. " +
                        "Forrige enhet: ${siste.ansvarligEnhetKode}, ny: ${bqSak.ansvarligEnhetKode}."
            )
            PrometheusProvider.prometheus.sammeEndretTid().increment()
            bqSak.copy(endretTid = siste.endretTid.plusNanos(1000))
        }
        siste.endretTid > bqSak.endretTid -> {
            val eksisterende = sakstatistikkRepository.hentHendelseMedEndretTid(
                bqSak.behandlingUUID, bqSak.endretTid, bqSak.erResending
            )
            if (eksisterende?.ansesSomDuplikat(bqSak) == true) {
                log.info(
                    "Forsinket hendelse allerede lagret med samme endretTid — hopper over. " +
                            "Referanse: ${bqSak.behandlingUUID}. endretTid: ${bqSak.endretTid}."
                )
                null
            } else {
                log.warn(
                    "Ny hendelse har eldre endretTid enn forrige lagrede rad — lagrer med opprinnelig tidsstempel. " +
                            "Referanse: ${bqSak.behandlingUUID}. " +
                            "Forrige endretTid: ${siste.endretTid}, ny: ${bqSak.endretTid}. " +
                            "Forrige status: ${siste.behandlingStatus}, ny status: ${bqSak.behandlingStatus}."
                )
                bqSak
            }
        }
        else -> bqSak
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

    private fun nærNokITid(
        originalTid: LocalDateTime,
        nyTid: LocalDateTime
    ): Boolean {
        val duration = Duration.between(originalTid, nyTid)
        return duration.abs().toMillis() <= 10
    }
}