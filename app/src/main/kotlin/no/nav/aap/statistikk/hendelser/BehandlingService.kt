package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.nyBehandlingOpprettet
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.skjerming.SkjermingService
import java.util.UUID

class BehandlingService(
    private val behandlingRepository: IBehandlingRepository,
    private val skjermingService: SkjermingService
) {
    constructor(repositoryProvider: RepositoryProvider, gatewayProvider: GatewayProvider) : this(
        repositoryProvider.provide(),
        SkjermingService.konstruer(gatewayProvider)
    )

    private val logger = org.slf4j.LoggerFactory.getLogger(javaClass)

    fun erSkjermet(behandling: Behandling): Boolean {
        return skjermingService.erSkjermet(behandling)
    }

    fun hentEllerLagreBehandling(
        dto: StoppetBehandling,
        sak: Sak
    ): Behandling {
        if (!Miljø.erProd()) {
            logger.info("Hent eller lagrer for sak ${sak.id}. DTO: $dto")
        }

        val behandling = konstruerBehandling(dto, sak)

        val eksisterendeBehandlingId = behandling.id
        val behandlingId = if (eksisterendeBehandlingId != null) {
            behandlingRepository.oppdaterBehandling(
                behandling.copy(id = eksisterendeBehandlingId)
            )
            logger.info("Oppdaterte behandling med referanse ${behandling.referanse} og id $eksisterendeBehandlingId.")
            eksisterendeBehandlingId
        } else {
            val id = behandlingRepository.opprettBehandling(behandling)
            logger.info("Opprettet behandling med referanse ${behandling.referanse} og id $id.")
            PrometheusProvider.prometheus.nyBehandlingOpprettet(dto.behandlingType.tilDomene())
                .increment()
            id
        }
        return behandling.copy(id = behandlingId)
    }


    fun konstruerBehandling(
        dto: StoppetBehandling,
        sak: Sak
    ): Behandling {
        val vedtakstidspunkt =
            dto.avsluttetBehandling?.vedtakstidspunkt ?: dto.avklaringsbehov.utledVedtakTid().let {
                if (it == null && dto.behandlingStatus.tilDomene() == BehandlingStatus.AVSLUTTET) dto.tidspunktSisteEndring else it
            }

        val (sisteLøsteAvklaringsbehov, sisteSaksbehandler) = dto.avklaringsbehov.utledForrigeLøsteAvklaringsbehov()
            ?: Pair(null, null)
        val behandling = Behandling(
            referanse = dto.behandlingReferanse,
            sak = sak,
            typeBehandling = dto.behandlingType.tilDomene(),
            opprettetTid = dto.behandlingOpprettetTidspunkt,
            vedtakstidspunkt = vedtakstidspunkt,
            ansvarligBeslutter = dto.avklaringsbehov.utledAnsvarligBeslutter(),
            mottattTid = dto.mottattTid,
            status = dto.behandlingStatus.tilDomene(),
            versjon = Versjon(verdi = dto.versjon),
            relaterteIdenter = dto.identerForSak,
            relatertBehandlingReferanse = dto.relatertBehandling?.toString(),
            sisteSaksbehandler = dto.avklaringsbehov.sistePersonPåBehandling(),
            sisteLøsteAvklaringsbehov = sisteLøsteAvklaringsbehov?.kode?.name,
            sisteSaksbehandlerSomLøstebehov = sisteSaksbehandler,
            gjeldendeAvklaringsBehov = dto.avklaringsbehov.utledGjeldendeAvklaringsbehov()?.kode?.name,
            gjeldendeAvklaringsbehovStatus = dto.avklaringsbehov.sisteAvklaringsbehovStatus(),
            søknadsformat = dto.soknadsFormat.tilDomene(),
            venteÅrsak = dto.avklaringsbehov.utledÅrsakTilSattPåVent(),
            returÅrsak = dto.avklaringsbehov.årsakTilRetur()?.name,
            resultat = dto.avsluttetBehandling?.resultat.resultatTilDomene(),
            gjeldendeStegGruppe = dto.avklaringsbehov.utledGjeldendeStegType()?.gruppe,
            årsaker = dto.vurderingsbehov.map { it.tilDomene() },
            opprettetAv = dto.opprettetAv,
            årsakTilOpprettelse = dto.årsakTilOpprettelse,
            oppdatertTidspunkt = dto.avklaringsbehov.tidspunktSisteEndring()
                ?: dto.tidspunktSisteEndring ?: dto.hendelsesTidspunkt
        )
        val eksisterendeBehandlingId = behandlingRepository.hent(dto.behandlingReferanse)?.id

        val relatertBehandling = hentRelatertBehandling(dto)
        val behandlingMedRelatertBehandling =
            behandling.copy(relatertBehandlingId = relatertBehandling?.id)
        return behandlingMedRelatertBehandling.copy(id = eksisterendeBehandlingId)
    }

    private fun hentRelatertBehandling(dto: StoppetBehandling): Behandling? {
        val relatertBehandlingUUID = dto.relatertBehandling
        val relatertBehadling =
            relatertBehandlingUUID?.let { behandlingRepository.hent(relatertBehandlingUUID) }

        if (relatertBehadling == null && relatertBehandlingUUID != null) {
            logger.warn("Fant ikke relatert behandling med UUID $relatertBehandlingUUID for behandling ${dto.behandlingReferanse}.")
        }
        return relatertBehadling
    }

    fun hentRelatertBehandlingUUID(behandling: Behandling): String? {
        val eksisterendeBehandling =
            behandling.relatertBehandlingId?.let { behandlingRepository.hent(it) }?.referanse
        return eksisterendeBehandling?.toString() ?: behandling.relatertBehandlingReferanse
    }

    fun hentBehandling(behandlingId: BehandlingId) = behandlingRepository.hent(behandlingId)

    fun hentBehandling(behandlingReferanse: UUID) = behandlingRepository.hent(behandlingReferanse)
}