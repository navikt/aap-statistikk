package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingHendelse
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.BehandlingTilstand
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.behandling.tilAvklaringsbehovKode
import no.nav.aap.statistikk.behandling.tilDomene
import no.nav.aap.statistikk.behandling.tilTilstand
import no.nav.aap.statistikk.nyBehandlingOpprettet
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.skjerming.SkjermingService
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.*

class BehandlingService(
    private val behandlingRepository: IBehandlingRepository,
    private val skjermingService: SkjermingService,
) {
    constructor(repositoryProvider: RepositoryProvider, gatewayProvider: GatewayProvider) : this(
        repositoryProvider.provide(),
        SkjermingService(gatewayProvider.provide()),
    )

    private val logger = LoggerFactory.getLogger(javaClass)

    fun erSkjermet(behandling: Behandling): Boolean = skjermingService.erSkjermet(behandling.identerPåBehandling())

    fun hentEllerLagreBehandling(dto: StoppetBehandling, sak: Sak): Behandling {
        if (!Miljø.erProd()) {
            logger.info("Hent eller lagrer for sak ${sak.id}. DTO: $dto")
        }

        val eksisterendeBehandling = behandlingRepository.hentBehandlingForUpdate(dto.behandlingReferanse)
        val behandling = konstruerBehandling(dto, sak, eksisterendeBehandling)

        return behandling.id?.let { eksisterendeBehandlingId ->
            behandlingRepository.oppdaterBehandling(behandling.copy(id = eksisterendeBehandlingId))
            logger.info("Oppdaterte behandling med referanse ${behandling.referanse} og id $eksisterendeBehandlingId.")
            behandling.copy(id = eksisterendeBehandlingId)
        } ?: run {
            opprettBehandling(dto = dto, behandling = behandling)
        }
    }

    private fun opprettBehandling(dto: StoppetBehandling, behandling: Behandling): Behandling = try {
        val id = behandlingRepository.opprettBehandling(behandling)
        logger.info("Opprettet behandling med referanse ${behandling.referanse} og id $id.")
        PrometheusProvider.prometheus.nyBehandlingOpprettet(dto.behandlingType.tilDomene())
            .increment()
        behandling.copy(id = id)
    } catch (e: SQLException) {
        if (e.erUniqueConstraintViolation()) {
            logger.warn(
                "Samtidighetskonflikt ved oppretting av behandling med referanse ${dto.behandlingReferanse}. Lar jobben retrye.",
                e,
            )
        }
        throw e
    }

    fun konstruerBehandling(
        dto: StoppetBehandling,
        sak: Sak,
        eksisterendeBehandlingKanskje: Behandling? = behandlingRepository.hent(dto.behandlingReferanse),
    ): Behandling {
        val tilstand = utledTilstand(dto)
        val eksisterendeBehandling = eksisterendeBehandlingKanskje?.leggTilHendelse(
            BehandlingHendelse(
                tidspunkt = null,
                hendelsesTidspunkt = dto.hendelsesTidspunkt,
                tilstand = tilstand,
            ),
        )

        val relatertBehandling = hentRelatertBehandling(dto)

        return eksisterendeBehandling?.copy(
            relatertBehandlingId = relatertBehandling?.id,
            relaterteIdenter = dto.identerForSak.map(::Ident),
            årsaker = if (dto.hendelsesTidspunkt < eksisterendeBehandlingKanskje.oppdatertTidspunkt()) {
                eksisterendeBehandlingKanskje.årsaker
            } else {
                dto.vurderingsbehov.map { it.tilDomene() }
            },
        ) ?: Behandling(
            referanse = dto.behandlingReferanse,
            sak = sak,
            typeBehandling = dto.behandlingType.tilDomene(),
            opprettetTid = dto.behandlingOpprettetTidspunkt,
            // Ny behandling har hittil ikke fått løsningstidspunktet før første historikkhendelse.
            tilstand = tilstand.copy(sistLøsteAvklaringsbehovTidspunkt = null),
            relaterteIdenter = dto.identerForSak.map(::Ident),
            årsaker = dto.vurderingsbehov.map { it.tilDomene() },
            opprettetAv = dto.opprettetAv,
            årsakTilOpprettelse = dto.årsakTilOpprettelse.name,
            oppdatertTidspunkt = dto.avklaringsbehov.tidspunktSisteEndring()
                ?: dto.tidspunktSisteEndring ?: dto.hendelsesTidspunkt,
        )
            .copy(relatertBehandlingId = relatertBehandling?.id)
    }

    private fun utledTilstand(dto: StoppetBehandling): BehandlingTilstand {
        val behandlingStatus = dto.behandlingStatus.tilDomene()
        val vedtakstidspunkt =
            dto.avsluttetBehandling?.vedtakstidspunkt ?: dto.avklaringsbehov.utledVedtakTid().let {
                if (it == null && behandlingStatus == BehandlingStatus.AVSLUTTET) {
                    dto.tidspunktSisteEndring
                } else {
                    it
                }
            }

        val behovstilstand = dto.avklaringsbehov.utledAvklaringsbehovTilstand()
        return BehandlingTilstand(
            status = behandlingStatus,
            versjon = Versjon(dto.versjon),
            mottattTid = dto.mottattTid,
            søknadsformat = dto.soknadsFormat.tilDomene(),
            ansvarligBeslutter = behovstilstand.ansvarligBeslutter,
            vedtakstidspunkt = vedtakstidspunkt,
            sisteSaksbehandler = behovstilstand.saksbehandler,
            resultat = dto.avsluttetBehandling?.resultat.resultatTilDomene(),
            gjeldendeAvklaringsBehov = behovstilstand.gjeldendeBehov?.definisjon?.tilAvklaringsbehovKode(),
            gjeldendeAvklaringsbehovStatus = behovstilstand.gjeldendeBehov?.status?.tilDomene(),
            sisteLøsteAvklaringsbehov = behovstilstand.sisteLøsteBehov?.definisjon?.tilAvklaringsbehovKode(),
            sisteSaksbehandlerSomLøstebehov = behovstilstand.sisteLøsteBehov?.løstAv,
            sistLøsteAvklaringsbehovTidspunkt = behovstilstand.sisteLøsteBehov?.tidspunkt,
            gjeldendeStegGruppe = behovstilstand.gjeldendeBehov?.steggruppe?.tilDomene(),
            venteÅrsak = behovstilstand.venteÅrsak,
            returÅrsak = behovstilstand.returÅrsak,
            returÅrsakkoblinger = behovstilstand.returÅrsakkoblinger.map { it.tilTilstand() },
            relatertBehandlingReferanse = dto.relatertBehandling?.toString(),
        )
    }

    private fun hentRelatertBehandling(dto: StoppetBehandling): Behandling? {
        val relatertBehandlingUUID = dto.relatertBehandling
        val relatertBehandling =
            relatertBehandlingUUID?.let { behandlingRepository.hent(relatertBehandlingUUID) }

        if (relatertBehandling == null && relatertBehandlingUUID != null) {
            logger.warn(
                "Fant ikke relatert behandling med UUID $relatertBehandlingUUID for behandling ${dto.behandlingReferanse}. Behandlingtype: ${dto.behandlingType}.",
            )
        }
        return relatertBehandling
    }

    fun hentRelatertBehandlingUUID(behandling: Behandling): String? {
        val eksisterendeBehandling =
            behandling.relatertBehandlingId?.let { behandlingRepository.hent(it) }?.referanse
        return eksisterendeBehandling?.toString() ?: behandling.nåværendeTilstand().relatertBehandlingReferanse
    }

    fun hentBehandling(behandlingId: BehandlingId) = behandlingRepository.hent(behandlingId)

    fun hentBehandling(behandlingReferanse: UUID) = behandlingRepository.hent(behandlingReferanse)
}

private fun SQLException.erUniqueConstraintViolation(): Boolean = generateSequence<Throwable>(this) { it.cause }
    .filterIsInstance<SQLException>()
    .any { it.sqlState == "23505" }
