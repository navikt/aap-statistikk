package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.DiagnoseEntity
import no.nav.aap.statistikk.behandling.DiagnoseRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.meldekort.FritaksvurderingRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import org.slf4j.LoggerFactory

class AvsluttetBehandlingService(
    private val tilkjentYtelseRepository: ITilkjentYtelseRepository,
    private val beregningsgrunnlagRepository: IBeregningsgrunnlagRepository,
    private val vilkårsResultatRepository: IVilkårsresultatRepository,
    private val diagnoseRepository: DiagnoseRepository,
    private val rettighetstypeperiodeRepository: IRettighetstypeperiodeRepository,
    private val fritaksvurderingRepository: FritaksvurderingRepository,
    private val behandlingService: BehandlingService,
    private val arbeidsopptrappingperioderRepository: ArbeidsopptrappingperioderRepository,
    private val opprettBigQueryLagringYtelseCallback: (BehandlingId) -> Unit,
) {
    private val logger = LoggerFactory.getLogger(AvsluttetBehandlingService::class.java)

    companion object {
        fun konstruer(
            gatewayProvider: GatewayProvider,
            repositoryProvider: RepositoryProvider,
            opprettBigQueryLagringYtelseCallback: (BehandlingId) -> Unit,
        ) = AvsluttetBehandlingService(
            tilkjentYtelseRepository = repositoryProvider.provide(),
            beregningsgrunnlagRepository = repositoryProvider.provide(),
            vilkårsResultatRepository = repositoryProvider.provide(),
            diagnoseRepository = repositoryProvider.provide(),
            behandlingService = BehandlingService(repositoryProvider, gatewayProvider),
            rettighetstypeperiodeRepository = repositoryProvider.provide(),
            arbeidsopptrappingperioderRepository = repositoryProvider.provide(),
            fritaksvurderingRepository = repositoryProvider.provide(),
            opprettBigQueryLagringYtelseCallback = opprettBigQueryLagringYtelseCallback
        )
    }

    fun lagre(avsluttetBehandling: AvsluttetBehandling) {
        lagreDiagnose(avsluttetBehandling)

        val uthentetBehandling =
            behandlingService.hentBehandling(avsluttetBehandling.behandlingsReferanse)

        if (uthentetBehandling != null) {
            vilkårsResultatRepository
                .lagreVilkårsResultat(
                    VilkårsResultatEntity.fraDomene(avsluttetBehandling.vilkårsresultat),
                    uthentetBehandling.id()
                )
        } else {
            error("Ingen behandling med referanse ${avsluttetBehandling.behandlingsReferanse}.")
        }

        tilkjentYtelseRepository.lagreTilkjentYtelse(
            TilkjentYtelseEntity.fraDomene(
                avsluttetBehandling.vedtakstidspunkt?.let {
                    avsluttetBehandling.tilkjentYtelse.begrensPerioderTil(
                        it.toLocalDate()
                    )
                }
                    ?: avsluttetBehandling.tilkjentYtelse
            )
        )

        if (avsluttetBehandling.beregningsgrunnlag != null) {
            beregningsgrunnlagRepository.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    value = avsluttetBehandling.beregningsgrunnlag,
                    behandlingsReferanse = avsluttetBehandling.behandlingsReferanse
                )
            )
        }

        arbeidsopptrappingperioderRepository.lagre(
            uthentetBehandling.id(),
            avsluttetBehandling.perioderMedArbeidsopptrapping
        )

        fritaksvurderingRepository.lagre(uthentetBehandling.id(), avsluttetBehandling.fritaksvurderinger)

        rettighetstypeperiodeRepository.lagre(
            avsluttetBehandling.behandlingsReferanse,
            avsluttetBehandling.rettighetstypeperioder
        )

        if (!behandlingService.erSkjermet(uthentetBehandling)) {
            opprettBigQueryLagringYtelseCallback(uthentetBehandling.id())
        } else {
            logger.info("Lagrer ikke i BigQuery fordi noen i saken er skjermet.")
        }
        PrometheusProvider.prometheus.avsluttetBehandlingLagret().increment()
    }

    private fun lagreDiagnose(avsluttetBehandling: AvsluttetBehandling) {
        if (avsluttetBehandling.diagnoser != null) {
            diagnoseRepository.lagre(
                DiagnoseEntity.fraDomene(
                    avsluttetBehandling.diagnoser,
                    avsluttetBehandling.behandlingsReferanse
                )
            )
        } else {
            logger.info("Ingen diagnose på behandling med referanse ${avsluttetBehandling.behandlingsReferanse}.")
        }
    }

}