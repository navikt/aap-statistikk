package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.avsluttetbehandling.IRettighetstypeperiodeRepository
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.bigquery.IBQSakstatistikkRepository
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.sak.BQBehandling
import no.nav.aap.statistikk.sak.BehandlingMetode
import no.nav.aap.statistikk.sak.IBigQueryKvitteringRepository
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
    private val clock: Clock = systemDefaultZone()
) {
    fun lagreSakInfoTilBigquery(behandlingId: Long) {

        val behandling = behandlingRepository.hent(behandlingId)
        val erSkjermet = skjermingService.erSkjermet(behandling)
        val saksbehandler =
            if (erSkjermet) "-5" else behandling.sisteSaksbehandler

        val versjon = behandling.versjon.verdi

        val sak = behandling.sak

        val sekvensNummer =
            bigQueryKvitteringRepository.lagreKvitteringForSak(sak, behandling)

        val hendelser = behandling.hendelser
        val erHosNAY = erHosNayNy(hendelser)
        val ansvarligEnhet = ansvarligEnhet(erHosNAY, behandling)

        val behandlingReferanse = behandling.referanse

        val erManuell = hendelser.erManuell()

        val relatertBehandlingUUID =
            behandling.relatertBehandlingId?.let { behandlingRepository.hent(it) }?.referanse

        val sakYtelse = regnUtSakYtelse(behandlingReferanse)

        val behandlingHendelse = hendelser.last()
        val hendelsesTidspunkt = behandlingHendelse.tidspunkt

        // TODO - kun om endring siden sist. somehow!?
        val bqSak = BQBehandling(
            sekvensNummer = sekvensNummer,
            behandlingUUID = behandlingReferanse.toString(),
            relatertBehandlingUUID = relatertBehandlingUUID?.toString(),
            relatertFagsystem = if (relatertBehandlingUUID != null) "Kelvin" else null,
            ferdigbehandletTid = if (behandling.status == BehandlingStatus.AVSLUTTET) hendelsesTidspunkt.truncatedTo(
                ChronoUnit.SECONDS
            ) else null,
            behandlingType = behandling.typeBehandling.toString().uppercase(),
            aktorId = sak.person.ident,
            saksnummer = sak.saksnummer,
            tekniskTid = LocalDateTime.now(clock),
            registrertTid = behandling.opprettetTid.truncatedTo(ChronoUnit.SECONDS),
            endretTid = hendelsesTidspunkt,
            verson = versjon,
            avsender = KELVIN,
            mottattTid = behandling.mottattTid.truncatedTo(ChronoUnit.SECONDS),
            // TODO: ved manuell revurdering må opprettetAv settes til saksbehandler som opprettet manuell revurdering
            opprettetAv = KELVIN,
            ansvarligBeslutter = if (erSkjermet && behandling.ansvarligBeslutter !== null) "-5" else behandling.ansvarligBeslutter,
            vedtakTid = behandling.vedtakstidspunkt?.truncatedTo(ChronoUnit.SECONDS),
            søknadsFormat = behandling.søknadsformat,
            saksbehandler = saksbehandler,
            behandlingMetode = if (erManuell) BehandlingMetode.MANUELL else BehandlingMetode.AUTOMATISK,
            behandlingStatus = behandlingStatus(behandling),
            behandlingÅrsak = behandling.årsaker.joinToString(","),
            ansvarligEnhetKode = ansvarligEnhet,
            sakYtelse = sakYtelse
        )

        if (behandling.årsaker.size > 1) {
            logger.warn("Behandling med referanse $behandlingReferanse hadde mer enn én årsak. Avgir den første.")
        }
        bigQueryRepository.lagre(bqSak)
    }

    private fun regnUtSakYtelse(
        behandlingReferanse: UUID
    ): String {
        val rettighetstyper = rettighetstypeperiodeRepository.hent(behandlingReferanse)
        return if (rettighetstyper.isEmpty()) {
            "AAP"
        } else {
            if (rettighetstyper.size > 1) {
                logger.info("Mer enn én rettighetstype for behandling $behandlingReferanse. Velger første.")
            }
            val førsteRettighetstype = rettighetstyper.first().rettighetstype.name.lowercase()
            "AAP_$førsteRettighetstype"
        }
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

    fun behandlingStatus(behandling: Behandling): String {
        // TODO: når klage er implementert, må dette fikses her
        // TODO: få inn retur fra kvalitetssikrer her, og ventegrunner

        val venteÅrsak = behandling.venteÅrsak?.let { "_$it" }.orEmpty()
        return when (behandling.status) {
            BehandlingStatus.OPPRETTET -> "REGISTRERT"
            BehandlingStatus.UTREDES -> "UNDER_BEHANDLING$venteÅrsak"
            BehandlingStatus.IVERKSETTES,
            BehandlingStatus.AVSLUTTET -> "AVSLUTTET"
        }
    }
}