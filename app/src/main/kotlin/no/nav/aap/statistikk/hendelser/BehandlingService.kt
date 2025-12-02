package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.nyBehandlingOpprettet
import no.nav.aap.statistikk.sak.Sak
import java.util.UUID

class BehandlingService(private val behandlingRepository: IBehandlingRepository) {
    constructor(repositoryProvider: RepositoryProvider) : this(repositoryProvider.provide())

    private val logger = org.slf4j.LoggerFactory.getLogger(javaClass)

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
            sisteSaksbehandler = dto.avklaringsbehov.sistePersonPåBehandling(),
            gjeldendeAvklaringsBehov = dto.avklaringsbehov.utledGjeldendeAvklaringsBehov()?.kode?.name,
            gjeldendeAvklaringsbehovStatus = dto.avklaringsbehov.sisteAvklaringsbehovStatus(),
            søknadsformat = dto.soknadsFormat.tilDomene(),
            venteÅrsak = dto.avklaringsbehov.utledÅrsakTilSattPåVent(),
            returÅrsak = dto.avklaringsbehov.årsakTilRetur()?.name,
            resultat = dto.avsluttetBehandling?.resultat.resultatTilDomene(),
            gjeldendeStegGruppe = dto.avklaringsbehov.utledGjeldendeStegType()?.gruppe,
            årsaker = dto.vurderingsbehov.map { it.tilDomene() },
            opprettetAv = dto.opprettetAv,
            oppdatertTidspunkt = dto.avklaringsbehov.tidspunktSisteEndring()
                ?: dto.tidspunktSisteEndring ?: dto.hendelsesTidspunkt
        )
        val eksisterendeBehandlingId = behandlingRepository.hent(dto.behandlingReferanse)?.id

        val relatertBehadling = hentRelatertBehandling(dto.relatertBehandling)
        val behandlingMedRelatertBehandling =
            behandling.copy(relatertBehandlingId = relatertBehadling?.id)
        return behandlingMedRelatertBehandling.copy(id = eksisterendeBehandlingId)
    }

    private fun hentRelatertBehandling(relatertBehandlingUUID: UUID?): Behandling? {
        val relatertBehadling =
            relatertBehandlingUUID?.let { behandlingRepository.hent(relatertBehandlingUUID) }
        return relatertBehadling
    }
}