package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.nyBehandlingOpprettet
import no.nav.aap.statistikk.oppgave.Saksbehandler
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.skjerming.SkjermingService
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.*

class BehandlingService(
    private val behandlingRepository: IBehandlingRepository,
    private val skjermingService: SkjermingService
) {
    constructor(repositoryProvider: RepositoryProvider, gatewayProvider: GatewayProvider) : this(
        repositoryProvider.provide(),
        SkjermingService(gatewayProvider.provide())
    )

    private val logger = LoggerFactory.getLogger(javaClass)

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

        val eksisterendeBehandling = behandlingRepository.hentBehandlingForUpdate(dto.behandlingReferanse)
        val behandling = konstruerBehandling(dto, sak, eksisterendeBehandling)

        val eksisterendeBehandlingId = behandling.id
        val lagretBehandling = if (eksisterendeBehandlingId != null) {
            behandlingRepository.oppdaterBehandling(
                behandling.copy(id = eksisterendeBehandlingId)
            )
            logger.info("Oppdaterte behandling med referanse ${behandling.referanse} og id $eksisterendeBehandlingId.")
            behandling.copy(id = eksisterendeBehandlingId)
        } else {
            try {
                val id = behandlingRepository.opprettBehandling(behandling)
                logger.info("Opprettet behandling med referanse ${behandling.referanse} og id $id.")
                PrometheusProvider.prometheus.nyBehandlingOpprettet(dto.behandlingType.tilDomene())
                    .increment()
                behandling.copy(id = id)
            } catch (e: SQLException) {
                if (!e.erUniqueConstraintViolation()) throw e

                val låstEksisterende = requireNotNull(
                    behandlingRepository.hentBehandlingForUpdate(dto.behandlingReferanse)
                ) {
                    "Fant ikke behandling med referanse ${dto.behandlingReferanse} etter unique violation."
                }

                val oppdatertBehandling = konstruerBehandling(dto, sak, låstEksisterende)
                behandlingRepository.oppdaterBehandling(oppdatertBehandling.copy(id = låstEksisterende.id))

                logger.info("Oppdaterte eksisterende behandling etter race på referanse ${dto.behandlingReferanse}.")
                oppdatertBehandling.copy(id = låstEksisterende.id())
            }
        }
        return lagretBehandling
    }


    fun konstruerBehandling(
        dto: StoppetBehandling,
        sak: Sak,
        eksisterendeBehandlingKanskje: Behandling? = behandlingRepository.hent(dto.behandlingReferanse)
    ): Behandling {
        val vedtakstidspunkt =
            dto.avsluttetBehandling?.vedtakstidspunkt ?: dto.avklaringsbehov.utledVedtakTid().let {
                if (it == null && dto.behandlingStatus.tilDomene() == BehandlingStatus.AVSLUTTET) dto.tidspunktSisteEndring else it
            }

        val (sisteLøsteAvklaringsbehov, sisteSaksbehandler, sistLøsteAvklaringsbehovTidspunkt) = dto.avklaringsbehov.utledForrigeLøsteAvklaringsbehov()
            ?: Triple(null, null, null)

        val eksisterendeBehandling = eksisterendeBehandlingKanskje?.leggTilHendelse(
            BehandlingHendelse(
                tidspunkt = null,
                hendelsesTidspunkt = dto.hendelsesTidspunkt,
                avklaringsBehov = dto.avklaringsbehov.utledGjeldendeAvklaringsbehov(),
                sisteLøsteAvklaringsbehov = sisteLøsteAvklaringsbehov,
                sisteSaksbehandlerSomLøstebehov = sisteSaksbehandler,
                sistLøsteAvklaringsbehovTidspunkt = sistLøsteAvklaringsbehovTidspunkt,
                steggruppe = dto.avklaringsbehov.utledGjeldendeAvklaringsbehov()?.løsesISteg?.gruppe,
                avklaringsbehovStatus = dto.avklaringsbehov.sisteAvklaringsbehovStatus(),
                venteÅrsak = dto.avklaringsbehov.utledÅrsakTilSattPåVent(),
                returÅrsak = dto.avklaringsbehov.årsakTilRetur()?.name,
                saksbehandler = dto.avklaringsbehov.sistePersonPåBehandling()
                    ?.let(::Saksbehandler),
                resultat = dto.avsluttetBehandling?.resultat.resultatTilDomene(),
                versjon = Versjon(verdi = dto.versjon),
                status = dto.behandlingStatus.tilDomene(),
                ansvarligBeslutter = dto.avklaringsbehov.utledAnsvarligBeslutter(),
                vedtakstidspunkt = vedtakstidspunkt,
                mottattTid = dto.mottattTid,
                søknadsformat = dto.soknadsFormat.tilDomene(),
                relatertBehandlingReferanse = dto.relatertBehandling?.toString()
            )
        )

        val relatertBehandling = hentRelatertBehandling(dto)

        if (eksisterendeBehandling == null) {
            val nyBehandling = Behandling(
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
                sisteLøsteAvklaringsbehov = sisteLøsteAvklaringsbehov,
                sisteSaksbehandlerSomLøstebehov = sisteSaksbehandler,
                gjeldendeAvklaringsBehov = dto.avklaringsbehov.utledGjeldendeAvklaringsbehov(),
                gjeldendeAvklaringsbehovStatus = dto.avklaringsbehov.sisteAvklaringsbehovStatus(),
                søknadsformat = dto.soknadsFormat.tilDomene(),
                venteÅrsak = dto.avklaringsbehov.utledÅrsakTilSattPåVent(),
                returÅrsak = dto.avklaringsbehov.årsakTilRetur()?.name,
                resultat = dto.avsluttetBehandling?.resultat.resultatTilDomene(),
                gjeldendeStegGruppe = dto.avklaringsbehov.utledGjeldendeAvklaringsbehov()?.løsesISteg?.gruppe,
                årsaker = dto.vurderingsbehov.map { it.tilDomene() },
                opprettetAv = dto.opprettetAv,
                årsakTilOpprettelse = dto.årsakTilOpprettelse.name,
                oppdatertTidspunkt = dto.avklaringsbehov.tidspunktSisteEndring()
                    ?: dto.tidspunktSisteEndring ?: dto.hendelsesTidspunkt
            )

            val behandlingMedRelatertBehandling =
                nyBehandling.copy(relatertBehandlingId = relatertBehandling?.id)

            return behandlingMedRelatertBehandling
        } else {
            return eksisterendeBehandling.copy(
                relatertBehandlingId = relatertBehandling?.id,
                relaterteIdenter = dto.identerForSak,
            )
        }
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

private fun SQLException.erUniqueConstraintViolation(): Boolean {
    var exception: Throwable? = this
    while (exception != null) {
        if (exception is SQLException && exception.sqlState == "23505") {
            return true
        }
        exception = exception.cause
    }
    return false
}