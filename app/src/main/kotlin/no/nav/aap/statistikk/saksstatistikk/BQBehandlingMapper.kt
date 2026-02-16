package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.avsluttetbehandling.IRettighetstypeperiodeRepository
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.hendelser.returnert
import no.nav.aap.statistikk.oppgave.OppgaveRepository
import no.nav.aap.statistikk.årsakTilOpprettelseIkkeSatt
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Clock.systemDefaultZone
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class BQBehandlingMapper(
    private val behandlingService: BehandlingService,
    private val rettighetstypeperiodeRepository: IRettighetstypeperiodeRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val sakstatistikkEventSourcing: SakstatistikkEventSourcing,
    private val clock: Clock = systemDefaultZone()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        fun konstruer(
            repositoryProvider: RepositoryProvider,
            gatewayProvider: GatewayProvider,
            clock: Clock = systemDefaultZone()
        ): BQBehandlingMapper {
            return BQBehandlingMapper(
                behandlingService = BehandlingService(repositoryProvider, gatewayProvider),
                rettighetstypeperiodeRepository = repositoryProvider.provide(),
                oppgaveRepository = repositoryProvider.provide(),
                sakstatistikkEventSourcing = SakstatistikkEventSourcing(),
                clock = clock
            )
        }
    }

    fun bqBehandlingForBehandling(
        behandling: Behandling,
        erSkjermet: Boolean,
    ): List<BQBehandling> {
        val sak = behandling.sak
        val relatertBehandlingUUID =
            behandlingService.hentRelatertBehandlingUUID(behandling)
        val behandlingReferanse = behandling.referanse

        val oppgaver = oppgaveRepository.hentOppgaverForBehandling(behandling.id())
        val snapshots = sakstatistikkEventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)

        val ansvarligEnhet = if (erSkjermet) "-5" else ansvarligEnhet(behandling, snapshots)

        if (ansvarligEnhet == null) log.info("Ansvarlig enhet er ikke satt. Behandling: $behandlingReferanse. Sak: ${sak.saksnummer}. Status: ${behandling.behandlingStatus()}. Årsak: ${behandling.årsakTilOpprettelse}")

        val saksbehandler = if (erSkjermet) "-5" else utledSaksbehandler(behandling, snapshots)

        if (saksbehandler == null) log.info("Saksbehandler er ikke satt. Behandling: $behandlingReferanse. Sak: ${sak.saksnummer}.")

        val årsakTilOpprettelse = behandling.årsakTilOpprettelse
        if (årsakTilOpprettelse == null) {
            log.info("Årsak til opprettelse er ikke satt. Behandling: $behandlingReferanse. Sak: ${sak.saksnummer}.")
            PrometheusProvider.prometheus.årsakTilOpprettelseIkkeSatt().increment()
        }

        if (behandling.mottattTid.isAfter(behandling.opprettetTid)) {
            log.info("Mottatt-tid er større enn opprettet-tid. Behandling: $behandlingReferanse. Mottatt: ${behandling.mottattTid}, opprettet: ${behandling.opprettetTid}.")
        }

        if (ansvarligEnhet == null && behandling.behandlingMetode() != BehandlingMetode.AUTOMATISK) {
            log.warn("Fant ikke enhet for behandling $behandlingReferanse og sak: ${sak.saksnummer}. Avklaringsbehov: ${behandling.gjeldendeAvklaringsBehov}. Sist løst avklaringsbehov: ${behandling.sisteLøsteAvklaringsbehov}")
        }

        return listOf(
            byggBQBehandling(
                behandling = behandling,
                relatertBehandlingUUID = relatertBehandlingUUID,
                erSkjermet = erSkjermet,
                ansvarligEnhet = ansvarligEnhet,
                saksbehandler = saksbehandler,
                endretTid = behandling.oppdatertTidspunkt(),
                behandlingStatus = behandlingStatus(behandling, snapshots)
            )
        )
    }

    private fun byggBQBehandling(
        behandling: Behandling,
        relatertBehandlingUUID: String?,
        erSkjermet: Boolean,
        ansvarligEnhet: String?,
        saksbehandler: String?,
        endretTid: LocalDateTime,
        behandlingStatus: String
    ): BQBehandling {
        val årsakTilOpprettelse = behandling.årsakTilOpprettelse
        val sak = behandling.sak

        val hendelser = behandling.hendelser
        val ansvarligBeslutter = behandling.ansvarligBeslutter()
        val behandlingReferanse = behandling.referanse

        return BQBehandling(
            behandlingUUID = behandlingReferanse,
            relatertBehandlingUUID = relatertBehandlingUUID,
            relatertFagsystem = if (relatertBehandlingUUID != null) "Kelvin" else null,
            ferdigbehandletTid = hendelser.ferdigBehandletTid(),
            behandlingType = behandling.typeBehandling.toString().uppercase(),
            aktorId = sak.person.ident,
            saksnummer = sak.saksnummer.value,
            tekniskTid = LocalDateTime.now(clock),
            registrertTid = behandling.opprettetTid.truncatedTo(ChronoUnit.SECONDS),
            endretTid = endretTid,
            versjon = behandling.versjon(),
            mottattTid = behandling.mottattTid.truncatedTo(ChronoUnit.SECONDS),
            opprettetAv = behandling.opprettetAv ?: KELVIN,
            ansvarligBeslutter = if (erSkjermet && ansvarligBeslutter != null) "-5" else ansvarligBeslutter,
            vedtakTid = behandling.vedtakstidspunkt(),
            søknadsFormat = behandling.søknadsformat,
            saksbehandler = saksbehandler,
            behandlingMetode = behandling.behandlingMetode().also {
                if (it == BehandlingMetode.AUTOMATISK) log.info(
                    "Behandling $behandlingReferanse er automatisk behandlet. Behandlingtype ${behandling.typeBehandling}"
                )
            },
            behandlingStatus = behandlingStatus,
            behandlingÅrsak = årsakTilOpprettelse ?: behandling.årsaker.prioriterÅrsaker().name,
            behandlingResultat = regnUtBehandlingResultat(behandling),
            resultatBegrunnelse = resultatBegrunnelse(hendelser),
            ansvarligEnhetKode = ansvarligEnhet,
            sakYtelse = "AAP",
            erResending = false
        )
    }

    private fun utledSaksbehandler(
        behandling: Behandling,
        snapshots: List<SakstatistikkSnapshot>,
    ): String? {
        val saksbehandler = snapshots.lastOrNull()?.saksbehandler

        // For avsluttede behandlinger: bruk sisteSaksbehandler som fallback
        if (behandling.behandlingStatus() == BehandlingStatus.AVSLUTTET && saksbehandler == null) {
            return behandling.sisteSaksbehandler ?: behandling.sisteSaksbehandlerSomLøstebehov
            ?: oppgaveRepository.hentOppgaverForBehandling(
                behandling.id()
            )
                .maxByOrNull { it.sistEndret() }?.reservasjon?.reservertAv?.ident
        }

        return saksbehandler
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
                            rettighetstyper.first().rettighetstype.name.uppercase()
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

    private fun behandlingStatus(
        behandling: Behandling,
        snapshots: List<SakstatistikkSnapshot>
    ): String {
        val hendelse = behandling.hendelser.last()
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

    data class EnhetOgSaksbehandler(val enhet: String?, val saksbehandler: String?)

    /**
     * Resolver enhet og saksbehandler fra ferske oppgave-data for en gitt behandling.
     * Brukes ved retry når den opprinnelige behandlingstilstanden ble snapshottet.
     */
    fun hentEnhetOgSaksbehandler(behandling: Behandling, erSkjermet: Boolean): EnhetOgSaksbehandler {
        val oppgaver = oppgaveRepository.hentOppgaverForBehandling(behandling.id())
        val snapshots = sakstatistikkEventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)
        val enhet = if (erSkjermet) "-5" else ansvarligEnhet(behandling, snapshots)
        val saksbehandler = if (erSkjermet) "-5" else utledSaksbehandler(behandling, snapshots)
        return EnhetOgSaksbehandler(enhet, saksbehandler)
    }

    private fun ansvarligEnhet(
        behandling: Behandling,
        snapshots: List<SakstatistikkSnapshot>,
    ): String? {
        if (behandling.behandlingMetode() == BehandlingMetode.AUTOMATISK) {
            return "KELVIN_AUTOMATISK"
        }

        val enhet = snapshots.lastOrNull()?.enhet

        if (behandling.behandlingStatus() == BehandlingStatus.IVERKSETTES && enhet == null) {
            log.info("Fant ikke oppgave for iverksettes-steg. Behandling: ${behandling.referanse}. Sak: ${behandling.sak.saksnummer}.")
            // Fallback til siste enhet
            return oppgaveRepository.hentOppgaverForBehandling(behandling.id())
                .maxByOrNull { it.sistEndret() }?.enhet?.kode
        }

        if (behandling.behandlingStatus() == BehandlingStatus.AVSLUTTET && enhet == null) {
            return oppgaveRepository.hentOppgaverForBehandling(behandling.id())
                .maxByOrNull { it.sistEndret() }?.enhet?.kode
        }

        // Fallback: hvis ingen enhet fra oppgave-events, kan vi ikke utlede noe
        // (enhet fra behandlingsflyt finnes ikke, så vi må returnere null)
        return enhet
    }
}