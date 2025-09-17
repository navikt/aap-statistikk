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

private const val NAY_NASJONAL_KØ_KODE = "4491"

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
        require(behandling.typeBehandling !in listOf(TypeBehandling.Oppfølgingsbehandling))

        val erSkjermet = skjermingService.erSkjermet(behandling)
        val saksbehandler =
            if (erSkjermet) "-5" else behandling.sisteSaksbehandler

        val sak = behandling.sak

        val sekvensNummer =
            bigQueryKvitteringRepository.lagreKvitteringForSak(sak, behandling)

        val hendelser = behandling.hendelser
        val erHosNAY = erHosNay(hendelser)
        val enhet = if (behandling.gjeldendeAvklaringsBehov != null) {
            oppgaveHendelseRepository.hentEnhetForAvklaringsbehov(
                behandling.referanse,
                behandling.gjeldendeAvklaringsBehov
            ).lastOrNull()?.enhet
        } else null

        if (enhet == null) {
            logger.warn("Fant ikke enhet for behandling $behandlingId.")
        }

        val ansvarligEnhet = ansvarligEnhet(erHosNAY, behandling, enhet)

        val behandlingReferanse = behandling.referanse

        val relatertBehandlingUUID = hentRelatertBehandlingUUID(behandling)

        val behandlingHendelse = hendelser.last()
        val hendelsesTidspunkt = behandlingHendelse.tidspunkt

        val bqSak = BQBehandling(
            sekvensNummer = sekvensNummer,
            behandlingUUID = behandlingReferanse.toString(),
            relatertBehandlingUUID = relatertBehandlingUUID?.toString(),
            relatertFagsystem = if (relatertBehandlingUUID != null) "Kelvin" else null,
            ferdigbehandletTid = behandling.hendelser.ferdigBehandletTid(),
            behandlingType = behandling.typeBehandling.toString().uppercase(),
            aktorId = sak.person.ident,
            saksnummer = sak.saksnummer.value,
            tekniskTid = LocalDateTime.now(clock),
            registrertTid = behandling.opprettetTid.truncatedTo(ChronoUnit.SECONDS),
            endretTid = hendelsesTidspunkt,
            verson = behandling.versjon.verdi,
            mottattTid = behandling.mottattTid.truncatedTo(ChronoUnit.SECONDS),
            // TODO: ved manuell revurdering må opprettetAv settes til saksbehandler som opprettet manuell revurdering
            opprettetAv = KELVIN,
            ansvarligBeslutter = if (erSkjermet && behandling.ansvarligBeslutter !== null) "-5" else behandling.ansvarligBeslutter,
            vedtakTid = behandling.vedtakstidspunkt?.truncatedTo(ChronoUnit.SECONDS),
            søknadsFormat = behandling.søknadsformat,
            saksbehandler = saksbehandler,
            behandlingMetode = behandling.behandlingMetode().also {
                if (it == BehandlingMetode.AUTOMATISK) logger.info(
                    "Behandling $behandlingReferanse er automatisk behandlet. Behandling $behandling"
                )
            },
            behandlingStatus = behandlingStatus(behandlingHendelse),
            behandlingÅrsak = behandling.årsaker.prioriterÅrsaker().name,
            ansvarligEnhetKode = ansvarligEnhet,
            behandlingResultat = regnUtBehandlingResultat(behandling),
            resultatBegrunnelse = resultatBegrunnelse(behandling.hendelser),
            sakYtelse = "AAP"
        )

        if (behandling.årsaker.size > 1) {
            logger.info("Behandling med referanse $behandlingReferanse hadde mer enn én årsak. Avgir den første.")
        }

        // TODO - kun lagre om endring siden sist
        bigQueryRepository.lagre(bqSak)
    }

    private fun hentRelatertBehandlingUUID(behandling: Behandling): UUID? =
        behandling.relatertBehandlingId?.let { behandlingRepository.hent(it) }?.referanse

    fun alleHendelserPåBehandling(behandlingId: BehandlingId): List<BQBehandling> {
        val behandling = behandlingRepository.hent(behandlingId)
        val sak = behandling.sak

        val erSkjermet = skjermingService.erSkjermet(behandling)

        return (1..<behandling.hendelser.size + 1).map { behandling.hendelser.subList(0, it) }
            .map { hendelser ->
                val sekvensNummer =
                    bigQueryKvitteringRepository.lagreKvitteringForSak(sak, behandling)
                val relatertBehandlingUUID = hentRelatertBehandlingUUID(behandling)

                val sisteHendelse = hendelser.last()
                val enhet = if (sisteHendelse.avklaringsBehov != null) {
                    oppgaveHendelseRepository.hentEnhetForAvklaringsbehov(
                        behandling.referanse,
                        sisteHendelse.avklaringsBehov
                    ).lastOrNull { it.tidspunkt.isBefore(sisteHendelse.hendelsesTidspunkt) }?.enhet
                } else null

                val saksbehandler =
                    if (erSkjermet) "-5" else sisteHendelse.saksbehandler?.ident

                BQBehandling(
                    sekvensNummer = sekvensNummer,
                    behandlingUUID = behandling.referanse.toString(),
                    relatertBehandlingUUID = relatertBehandlingUUID?.toString(),
                    relatertFagsystem = if (relatertBehandlingUUID != null) "Kelvin" else null,
                    ferdigbehandletTid = hendelser.ferdigBehandletTid(),
                    behandlingType = behandling.typeBehandling.toString().uppercase(),
                    aktorId = sak.person.ident,
                    saksnummer = sak.saksnummer.value,
                    tekniskTid = LocalDateTime.now(clock),
                    registrertTid = behandling.opprettetTid.truncatedTo(ChronoUnit.SECONDS),
                    endretTid = sisteHendelse.hendelsesTidspunkt,
                    verson = sisteHendelse.versjon.verdi,
                    mottattTid = behandling.mottattTid.truncatedTo(ChronoUnit.SECONDS),
                    opprettetAv = KELVIN,
                    ansvarligBeslutter = if (erSkjermet && behandling.ansvarligBeslutter !== null) "-5" else behandling.ansvarligBeslutter,
                    vedtakTid = sisteHendelse.vedtakstidspunkt,
                    søknadsFormat = behandling.søknadsformat,
                    saksbehandler = saksbehandler,
                    behandlingMetode = behandling.copy(hendelser = hendelser).behandlingMetode(),
                    behandlingStatus = behandlingStatus(sisteHendelse),
                    behandlingÅrsak = behandling.årsaker.prioriterÅrsaker().name,
                    behandlingResultat = regnUtBehandlingResultat(behandling),
                    resultatBegrunnelse = resultatBegrunnelse(hendelser),
                    ansvarligEnhetKode = enhet,
                    sakYtelse = "AAP"
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
                val resultat = behandling.resultat

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
        erHosNAY: Boolean,
        behandling: Behandling,
        enhet: String?
    ): String? {
        if (skjermingService.erSkjermet(behandling)) {
            return "-5"
        }
        return enhet ?: if (erHosNAY) NAY_NASJONAL_KØ_KODE else null
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