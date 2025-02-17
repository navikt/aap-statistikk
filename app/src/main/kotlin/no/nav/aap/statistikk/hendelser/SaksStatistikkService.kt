package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.sak.BQBehandling
import no.nav.aap.statistikk.sak.BehandlingMetode
import no.nav.aap.statistikk.sak.IBigQueryKvitteringRepository
import no.nav.aap.statistikk.sak.Sak
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Clock.systemDefaultZone
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private val logger = LoggerFactory.getLogger(SaksStatistikkService::class.java)

private const val NAY_NASJONAL_KØ_KODE = "4491"

class SaksStatistikkService(
    private val behandlingRepository: IBehandlingRepository,
    private val bigQueryKvitteringRepository: IBigQueryKvitteringRepository,
    private val bigQueryRepository: IBQRepository,
    private val skjermingService: SkjermingService,
    private val clock: Clock = systemDefaultZone()
) {
    fun lagreSakInfoTilBigquery(
        sak: Sak,
        behandlingId: Long,
        versjon: String,
        hendelsesTidspunkt: LocalDateTime,
        vedtakTidspunkt: LocalDateTime?,
        erManuell: Boolean,
        ansvarligBeslutter: String?,
        erHosNAY: Boolean
    ) {
        val behandling = behandlingRepository.hent(behandlingId)
        val erSkjermet = skjermingService.erSkjermet(behandling)
        val saksbehandler =
            if (erSkjermet) "-5" else behandling.sisteSaksbehandler

        val sekvensNummer = bigQueryKvitteringRepository.lagreKvitteringForSak(sak, behandling)

        val ansvarligEnhet = ansvarligEnhet(erHosNAY, behandling)

        val relatertBehandlingUUID =
            behandling.relatertBehandlingId?.let { behandlingRepository.hent(it) }?.referanse

        // TODO - kun om endring siden sist. somehow!?
        val bqSak = BQBehandling(
            sekvensNummer = sekvensNummer,
            saksnummer = sak.saksnummer,
            behandlingUUID = behandling.referanse.toString(),
            behandlingType = behandling.typeBehandling.toString().uppercase(),
            tekniskTid = LocalDateTime.now(clock),
            avsender = KELVIN,
            verson = versjon,
            aktorId = sak.person.ident,
            mottattTid = behandling.mottattTid.truncatedTo(ChronoUnit.SECONDS),
            registrertTid = behandling.opprettetTid.truncatedTo(ChronoUnit.SECONDS),
            relatertBehandlingUUID = relatertBehandlingUUID?.toString(),
            relatertFagsystem = if (relatertBehandlingUUID != null) "Kelvin" else null,
            ferdigbehandletTid = if (behandling.status == BehandlingStatus.AVSLUTTET) hendelsesTidspunkt.truncatedTo(
                ChronoUnit.SECONDS // SJEKK OPP DENNE, er iverksettes før avsluttet
            ) else null,
            endretTid = hendelsesTidspunkt,
            ansvarligBeslutter = if (erSkjermet && ansvarligBeslutter !== null) "-5" else ansvarligBeslutter,
            opprettetAv = KELVIN,
            saksbehandler = saksbehandler,
            vedtakTid = vedtakTidspunkt?.truncatedTo(ChronoUnit.SECONDS),
            søknadsFormat = behandling.søknadsformat,
            behandlingMetode = if (erManuell) BehandlingMetode.MANUELL else BehandlingMetode.AUTOMATISK,
            behandlingStatus = behandlingStatus(behandling.status),
            behandlingÅrsak = behandling.årsaker.joinToString(","),
            ansvarligEnhetKode = ansvarligEnhet
        )

        if (behandling.årsaker.size > 1) {
            logger.warn("Behandling med referanse ${behandling.referanse} hadde mer enn én årsak. Avgir den første.")
        }
        bigQueryRepository.lagre(bqSak)
    }

    private fun ansvarligEnhet(
        erHosNAY: Boolean,
        behandling: Behandling
    ): String? {
        if (skjermingService.erSkjermet(behandling)) {
            return "-5"
        }
        return if (erHosNAY) NAY_NASJONAL_KØ_KODE else behandling.behandlendeEnhet?.kode
    }

    fun behandlingStatus(status: BehandlingStatus): String {
        // TODO: når klage er implementert, må dette fikses her
        // TODO: få inn retur fra kvalitetssikrer her, og ventegrunner
        return when (status) {
            BehandlingStatus.OPPRETTET -> "REGISTRERT"
            BehandlingStatus.UTREDES -> "UNDER_BEHANDLING"
            BehandlingStatus.IVERKSETTES -> "AVSLUTTET"
            BehandlingStatus.AVSLUTTET -> "AVSLUTTET"
        }
    }
}