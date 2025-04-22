package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.avsluttetbehandling.IRettighetstypeperiodeRepository
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingId
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
    fun lagreSakInfoTilBigquery(behandlingId: BehandlingId) {

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

        val relatertBehandlingUUID =
            behandling.relatertBehandlingId?.let { behandlingRepository.hent(it) }?.referanse


        val behandlingHendelse = hendelser.last()
        val hendelsesTidspunkt = behandlingHendelse.tidspunkt

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
            saksnummer = sak.saksnummer.value,
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
            behandlingMetode = behandlingMetode(behandling).also {
                if (it == BehandlingMetode.AUTOMATISK) logger.info(
                    "Behandling $behandlingReferanse er automatisk behandlet. Behandling $behandling"
                )
            },
            behandlingStatus = behandlingStatus(behandling),
            behandlingÅrsak = behandling.årsaker.joinToString(","),
            ansvarligEnhetKode = ansvarligEnhet,
            behandlingResultat = regnUtBehandlingResultat(behandling),
            resultatBegrunnelse = resultatBegrunnelse(behandling),
            sakYtelse = "AAP"
        )

        if (behandling.årsaker.size > 1) {
            logger.warn("Behandling med referanse $behandlingReferanse hadde mer enn én årsak. Avgir den første.")
        }

        // TODO - kun lagre om endring siden sist
        bigQueryRepository.lagre(bqSak)
    }

    private fun behandlingMetode(behandling: Behandling): BehandlingMetode {
        if (behandling.hendelser.isEmpty()) {
            logger.info("Behandling-hendelser var tom.")
            return BehandlingMetode.AUTOMATISK
        }
        val sisteHendelse = behandling.hendelser.last()
        if (sisteHendelse.avklaringsBehov.isNullOrBlank()) {
            logger.info("Ingen avkl.funnet for siste hendelse $sisteHendelse. Behandling: $behandling")
            return behandlingMetode(behandling.copy(hendelser = behandling.hendelser.dropLast(1)))
        }

        val sisteDefinisjon = Definisjon.forKode(sisteHendelse.avklaringsBehov)

        if (sisteDefinisjon == Definisjon.KVALITETSSIKRING) {
            return BehandlingMetode.KVALITETSSIKRING
        }

        if (sisteDefinisjon == Definisjon.FATTE_VEDTAK) {
            return BehandlingMetode.FATTE_VEDTAK
        }

        if (!behandling.hendelser.erManuell()) {
            logger.info("Hendelser: $behandling")
        }

        return if (behandling.hendelser.erManuell()) BehandlingMetode.MANUELL else BehandlingMetode.AUTOMATISK
    }

    /**
     * Om behandlingen er returnert fra kvalitetssikrer, skal dette være returårsaken.
     *
     * TODO: begrunnelse ved avslag/innvilgelse
     */
    private fun resultatBegrunnelse(behandling: Behandling): String? {
        if (behandling.hendelser.last().avklaringsbehovStatus?.returnert() == true) {
            return behandling.hendelser.last().returÅrsak
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
                    null -> null
                }
            }
        }
    }

    private fun ansvarligEnhet(
        erHosNAY: Boolean,
        behandling: Behandling
    ): String? {
        if (skjermingService.erSkjermet(behandling)) {
            return "-5"
        }
        // TODO! Hvordan fange opp 4438 her?
        return if (erHosNAY) NAY_NASJONAL_KØ_KODE else behandling.behandlendeEnhet?.kode
    }

    fun behandlingStatus(behandling: Behandling): String {
        // TODO: når klage er implementert, må dette fikses her

        val venteÅrsak = behandling.venteÅrsak?.let { "_${it.uppercase()}" }.orEmpty()
        val returStatus = behandling.gjeldendeAvklaringsbehovStatus
            ?.takeIf { it.returnert() }
            ?.let { "_${it.name.uppercase()}" }.orEmpty()


        return when (behandling.status) {
            BehandlingStatus.OPPRETTET -> "REGISTRERT"
            BehandlingStatus.UTREDES -> "UNDER_BEHANDLING$venteÅrsak$returStatus"
            BehandlingStatus.IVERKSETTES -> "IVERKSETTES"
            BehandlingStatus.AVSLUTTET -> "AVSLUTTET"
        }
    }
}