package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.sakDuplikat
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Clock.systemDefaultZone
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class SaksStatistikkService(
    private val behandlingService: BehandlingService,
    private val sakstatistikkRepository: SakstatistikkRepository,
    private val bqBehandlingMapper: BQBehandlingMapper,
) : ISaksStatistikkService {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        fun konstruer(
            gatewayProvider: GatewayProvider,
            repositoryProvider: RepositoryProvider,
            clock: Clock = systemDefaultZone()
        ): SaksStatistikkService {
            return SaksStatistikkService(
                behandlingService = BehandlingService(repositoryProvider, gatewayProvider),
                sakstatistikkRepository = repositoryProvider.provide(),
                bqBehandlingMapper = BQBehandlingMapper.konstruer(
                    repositoryProvider,
                    gatewayProvider,
                    clock
                ),
            )
        }
    }

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

        val bqSaker =
            bqBehandlingMapper.bqBehandlingForBehandling(behandling, erSkjermet)

        val manglerEnhet = !lagreUtenEnhet && bqSaker.any {
            it.ansvarligEnhetKode == null && it.behandlingMetode != BehandlingMetode.AUTOMATISK
        }

        if (manglerEnhet) {
            return SakStatistikkResultat.ManglerEnhet(
                behandlingId = behandling.id(),
                avklaringsbehovKode = behandling.gjeldendeAvklaringsBehov,
                hendelsestid = behandling.oppdatertTidspunkt()
            )
        }

        val manglerFortsattEnhet =
            bqSaker.any { it.ansvarligEnhetKode == null && it.behandlingMetode != BehandlingMetode.AUTOMATISK }
        if (manglerFortsattEnhet) {
            val referanse = behandling.referanse
            val saksnummer = behandling.sak.saksnummer
            log.warn("Ansvarlig enhet er ikke satt. Behandling: $referanse. Sak: $saksnummer. Status: ${behandling.behandlingStatus()}. Årsak: ${behandling.årsakTilOpprettelse}.")
            log.warn("Saksbehandler er ikke satt. Behandling: $referanse. Sak: $saksnummer. Status: ${behandling.behandlingStatus()}. Årsak: ${behandling.årsakTilOpprettelse}.")
        }

        bqSaker.forEach { bqSak ->
            lagreBQBehandling(bqSak)
        }

        return SakStatistikkResultat.OK
    }

    override fun lagreMedOppgavedata(
        behandlingId: BehandlingId,
        originalHendelsestid: LocalDateTime,
        lagreUtenEnhet: Boolean
    ): SakStatistikkResultat {
        val behandling = behandlingService.hentBehandling(behandlingId)
        require(
            behandling.typeBehandling in Konstanter.interessanteBehandlingstyper
        ) {
            "Denne jobben skal ikke kunne bli trigget av oppfølgingsbehandlinger. Behandling: ${behandling.referanse}"
        }

        // Bruk behandlingstilstanden slik den var ved opprinnelig hendelsestid
        val snapshotBehandling = behandling.påTidspunkt(originalHendelsestid)
        val erSkjermet = behandlingService.erSkjermet(behandling)

        val bqSaker =
            bqBehandlingMapper.bqBehandlingForBehandling(snapshotBehandling, erSkjermet)

        // Re-resolv enhet og saksbehandler fra ferske oppgave-data
        val (enhet, saksbehandler) = bqBehandlingMapper.hentEnhetOgSaksbehandler(
            behandling,
            erSkjermet
        )

        val bqSakerMedOppgavedata = bqSaker.map {
            it.copy(
                ansvarligEnhetKode = it.ansvarligEnhetKode ?: enhet,
                saksbehandler = it.saksbehandler ?: saksbehandler
            )
        }

        val manglerEnhet = !lagreUtenEnhet && bqSakerMedOppgavedata.any {
            it.ansvarligEnhetKode == null && it.behandlingMetode != BehandlingMetode.AUTOMATISK
        }

        if (manglerEnhet) {
            return SakStatistikkResultat.ManglerEnhet(
                behandlingId = behandling.id(),
                avklaringsbehovKode = snapshotBehandling.gjeldendeAvklaringsBehov,
                hendelsestid = originalHendelsestid
            )
        }

        bqSakerMedOppgavedata.forEach { bqSak ->
            lagreBQBehandling(bqSak)
        }

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

    fun alleHendelserPåBehandling(
        behandlingId: BehandlingId
    ): List<BQBehandling> {
        val behandling = behandlingService.hentBehandling(behandlingId)

        val erSkjermet = behandlingService.erSkjermet(behandling)

        val originaleHendelser =
            sakstatistikkRepository.hentAlleHendelserPåBehandling(behandling.referanse)

        val alleHendelser = behandling.hendelsesHistorikk()
            .flatMap { behandling ->
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