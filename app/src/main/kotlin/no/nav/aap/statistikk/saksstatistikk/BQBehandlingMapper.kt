package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.avsluttetbehandling.IRettighetstypeperiodeRepository
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.hendelser.returnert
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepository
import no.nav.aap.statistikk.årsakTilOpprettelseIkkeSatt
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Clock.systemDefaultZone
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class BQBehandlingMapper(
    private val behandlingService: BehandlingService,
    private val rettighetstypeperiodeRepository: IRettighetstypeperiodeRepository,
    private val oppgaveHendelseRepository: OppgaveHendelseRepository,
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
                oppgaveHendelseRepository = repositoryProvider.provide(),
                clock = clock
            )
        }
    }

    fun bqBehandlingForBehandling(
        behandling: Behandling,
        erSkjermet: Boolean,
        sekvensNummer: Long?
    ): BQBehandling {
        val sak = behandling.sak
        val relatertBehandlingUUID = behandlingService.hentRelatertBehandlingUUID(behandling)
        val hendelser = behandling.hendelser
        val sisteHendelse = hendelser.last()
        val behandlingReferanse = behandling.referanse

        val ansvarligEnhet = ansvarligEnhet(behandlingReferanse, behandling, erSkjermet)
        val saksbehandlerIdent = sisteHendelse.saksbehandler?.ident

        if (saksbehandlerIdent == null) {
            log.info("Fant ikke siste saksbehandler for behandling $behandlingReferanse. Avklaringsbehov: ${sisteHendelse.avklaringsBehov}.")
        }

        val årsakTilOpprettelse = behandling.årsakTilOpprettelse
        if (årsakTilOpprettelse == null) {
            log.info("Årsak til opprettelse er ikke satt. Behandling: $behandlingReferanse. Sak: ${sak.saksnummer}.")
            PrometheusProvider.prometheus.årsakTilOpprettelseIkkeSatt().increment()
        }

        val saksbehandler =
            if (erSkjermet) "-5" else saksbehandlerIdent

        if (behandling.mottattTid.isAfter(behandling.opprettetTid)) {
            log.info("Mottatt-tid er større enn opprettet-tid. Behandling: $behandlingReferanse. Mottatt: ${behandling.mottattTid}, opprettet: ${behandling.opprettetTid}.")
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
                    "Behandling $behandlingReferanse er automatisk behandlet. Behandling ${behandling.referanse}"
                )
            },
            behandlingStatus = behandlingStatus(behandling, sisteHendelse),
            behandlingÅrsak = årsakTilOpprettelse ?: behandling.årsaker.prioriterÅrsaker().name,
            behandlingResultat = regnUtBehandlingResultat(behandling),
            resultatBegrunnelse = resultatBegrunnelse(hendelser),
            ansvarligEnhetKode = ansvarligEnhet,
            sakYtelse = "AAP",
            erResending = false
        )
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
                    null -> "UDEFINERT"
                }
            }
        }
    }

    private fun behandlingStatus(behandling: Behandling, hendelse: BehandlingHendelse): String {
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

    private fun ansvarligEnhet(
        behandlingReferanse: UUID,
        behandling: Behandling,
        erSkjermet: Boolean,
    ): String? {
        val sisteHendelse = behandling.hendelser.last()
        val sisteHendelsevklaringsbehov =
            if (Miljø.erProd()) sisteHendelse.avklaringsBehov else sisteHendelse.sisteLøsteAvklaringsbehov
        val enhet = sisteHendelsevklaringsbehov?.let {
            oppgaveHendelseRepository.hentEnhetForAvklaringsbehov(
                behandlingReferanse,
                it
            )
        }?.lastOrNull {
            // I tilfelle enhet har flyttet seg på samme avklaringsbehov
            it.tidspunkt.isBefore(
                sisteHendelse.hendelsesTidspunkt.plusDays(1) // STYGT
            )
        }?.enhet

        val enhetMedSisteLøsteAvklaringsbehov = sisteHendelsevklaringsbehov?.let {
            oppgaveHendelseRepository.hentEnhetForAvklaringsbehov(
                behandlingReferanse,
                it
            )
        }?.lastOrNull {
            // I tilfelle enhet har flyttet seg på samme avklaringsbehov
            it.tidspunkt.isBefore(
                sisteHendelse.hendelsesTidspunkt.plusDays(1) // STYGT
            )
        }?.enhet

        log.info("Enhet gammel: $enhet. Enhet ny $enhetMedSisteLøsteAvklaringsbehov. Behandling: $behandlingReferanse.")

        if (enhet == null) {
            log.info("Fant ikke enhet for behandling $behandlingReferanse. Avklaringsbehov: $sisteHendelsevklaringsbehov. Typebehandling: ${behandling.typeBehandling}. Årsak til opprettelse: ${behandling.årsakTilOpprettelse}")
            val fallbackEnhet =
                oppgaveHendelseRepository.hentSisteEnhetPåBehandling(behandlingReferanse)

            if (fallbackEnhet != null) {
                val (enhetOgTidspunkt, avklaringsBehov) = fallbackEnhet
                val fallbackEnhet = enhetOgTidspunkt.enhet
                log.info("Fallback-enhet: $fallbackEnhet for avklaringsbehov ${avklaringsBehov}. Originalt behov: $sisteHendelsevklaringsbehov. Referanse: $behandlingReferanse. Typebehandling: ${behandling.typeBehandling}. Årsak til opprettelse: ${behandling.årsakTilOpprettelse}")
                return fallbackEnhet
            } else {
                log.info("Fant ingen enhet eller fallbackenhet. Referanse: $behandlingReferanse. Avklaringsbehov: $sisteHendelsevklaringsbehov. Typebehandling: ${behandling.typeBehandling}. Årsak til opprettelse: ${behandling.årsakTilOpprettelse}.")
            }
        }
        if (erSkjermet) {
            return "-5"
        }
        return enhet
    }


    fun List<BehandlingHendelse>.ferdigBehandletTid(): LocalDateTime? {
        return this.firstOrNull { it.status == BehandlingStatus.AVSLUTTET }?.hendelsesTidspunkt
    }
}