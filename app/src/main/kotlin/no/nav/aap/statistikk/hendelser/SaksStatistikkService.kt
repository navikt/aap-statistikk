package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.avsluttetbehandling.IRettighetstypeperiodeRepository
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.bigquery.IBQSakstatistikkRepository
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepository
import no.nav.aap.statistikk.sak.BQBehandling
import no.nav.aap.statistikk.sak.BehandlingMetode
import no.nav.aap.statistikk.sak.IBigQueryKvitteringRepository
import no.nav.aap.statistikk.skjerming.SkjermingService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Clock.systemDefaultZone
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

private val logger = LoggerFactory.getLogger(SaksStatistikkService::class.java)

class SaksStatistikkService(
    private val behandlingRepository: IBehandlingRepository,
    private val rettighetstypeperiodeRepository: IRettighetstypeperiodeRepository,
    private val bigQueryKvitteringRepository: IBigQueryKvitteringRepository,
    private val bigQueryRepository: IBQSakstatistikkRepository,
    private val skjermingService: SkjermingService,
    private val oppgaveHendelseRepository: OppgaveHendelseRepository,
    private val clock: Clock = systemDefaultZone()
) {
    fun lagreSakInfoTilBigquery(behandlingId: BehandlingId) {
        val behandling = behandlingRepository.hent(behandlingId)
        require(behandling.typeBehandling !in listOf(TypeBehandling.Oppfølgingsbehandling)) {
            "Denne jobben skal ikke kunne bli trigget av oppfølgingsbehandlinger. Behandling: ${behandling.referanse}"
        }

        val erSkjermet = skjermingService.erSkjermet(behandling)

        val sekvensNummer =
            bigQueryKvitteringRepository.lagreKvitteringForSak(behandling)

        val bqSak = bqBehandlingForBehandling(behandling, erSkjermet, sekvensNummer)

        // TODO - kun lagre om endring siden sist
        bigQueryRepository.lagre(bqSak)
    }

    private fun hentRelatertBehandlingUUID(behandling: Behandling): UUID? =
        behandling.relatertBehandlingId?.let { behandlingRepository.hent(it) }?.referanse

    fun bqBehandlingForBehandling(
        behandling: Behandling,
        erSkjermet: Boolean,
        sekvensNummer: Long
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
            logger.warn("Fant ikke enhet for behandling $behandlingReferanse.")
        }

        val saksbehandler =
            if (erSkjermet) "-5" else sisteHendelse.saksbehandler?.ident

        if (behandling.årsaker.size > 1) {
            logger.info("Behandling med referanse $behandlingReferanse hadde mer enn én årsak. Avgir den første.")
        }

        return BQBehandling(
            sekvensNummer = sekvensNummer,
            behandlingUUID = behandlingReferanse.toString(),
            relatertBehandlingUUID = relatertBehandlingUUID?.toString(),
            relatertFagsystem = if (relatertBehandlingUUID != null) "Kelvin" else null,
            ferdigbehandletTid = hendelser.ferdigBehandletTid(),
            behandlingType = behandling.typeBehandling.toString().uppercase(),
            aktorId = sak.person.ident,
            saksnummer = sak.saksnummer.value,
            tekniskTid = LocalDateTime.now(clock),
            registrertTid = behandling.opprettetTid.truncatedTo(ChronoUnit.SECONDS),
            endretTid = sisteHendelse.hendelsesTidspunkt,
            versjon = sisteHendelse.versjon.verdi,
            mottattTid = behandling.mottattTid.truncatedTo(ChronoUnit.SECONDS),
            // TODO: ved manuell revurdering må opprettetAv settes til saksbehandler som opprettet manuell revurdering
            opprettetAv = KELVIN,
            ansvarligBeslutter = if (erSkjermet && sisteHendelse.ansvarligBeslutter !== null) "-5" else sisteHendelse.ansvarligBeslutter,
            vedtakTid = sisteHendelse.vedtakstidspunkt,
            søknadsFormat = behandling.søknadsformat,
            saksbehandler = saksbehandler,
            behandlingMetode = behandling.behandlingMetode().also {
                if (it == BehandlingMetode.AUTOMATISK) logger.info(
                    "Behandling $behandlingReferanse er automatisk behandlet. Behandling $behandling"
                )
            },
            behandlingStatus = behandlingStatus(sisteHendelse),
            behandlingÅrsak = behandling.årsaker.prioriterÅrsaker().name,
            behandlingResultat = regnUtBehandlingResultat(behandling),
            resultatBegrunnelse = resultatBegrunnelse(hendelser),
            ansvarligEnhetKode = ansvarligEnhet,
            sakYtelse = "AAP"
        )
    }

    fun alleHendelserPåBehandling(behandlingId: BehandlingId): List<BQBehandling> {
        val behandling = behandlingRepository.hent(behandlingId)

        val erSkjermet = skjermingService.erSkjermet(behandling)

        return (1..<behandling.hendelser.size + 1).map { behandling.hendelser.subList(0, it) }
            .map { hendelser ->
                val sekvensNummer =
                    bigQueryKvitteringRepository.lagreKvitteringForSak(behandling)

                bqBehandlingForBehandling(
                    behandling.copy(hendelser = hendelser),
                    erSkjermet,
                    sekvensNummer
                )
            }
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
            BehandlingStatus.OPPRETTET -> null
            BehandlingStatus.UTREDES -> null
            BehandlingStatus.IVERKSETTES -> null
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
                            logger.info("Mer enn én rettighetstype for behandling $behandlingReferanse. Velger første.")
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