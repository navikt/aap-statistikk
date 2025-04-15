package no.nav.aap.statistikk.avsluttetbehandling

import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.behandling.DiagnoseEntity
import no.nav.aap.statistikk.behandling.DiagnoseRepository
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.pdl.SkjermingService
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
    private val behandlingRepository: IBehandlingRepository,
    private val rettighetstypeperiodeRepository: IRettighetstypeperiodeRepository,
    private val skjermingService: SkjermingService,
    private val meterRegistry: MeterRegistry,
    private val ytelsesStatistikkTilBigQuery: YtelsesStatistikkTilBigQuery,
) {
    private val logger = LoggerFactory.getLogger(AvsluttetBehandlingService::class.java)

    fun lagre(avsluttetBehandling: AvsluttetBehandling) {
        lagreDiagnose(avsluttetBehandling)

        val uthentetBehandling =
            behandlingRepository.hent(avsluttetBehandling.behandlingsReferanse)

        if (uthentetBehandling != null) {
            vilkårsResultatRepository
                .lagreVilkårsResultat(
                    VilkårsResultatEntity.fraDomene(avsluttetBehandling.vilkårsresultat),
                    uthentetBehandling.id!!
                )
        } else {
            error("Ingen behandling med referanse ${avsluttetBehandling.behandlingsReferanse}.")
        }

        tilkjentYtelseRepository.lagreTilkjentYtelse(
            TilkjentYtelseEntity.fraDomene(
                avsluttetBehandling.tilkjentYtelse
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

        rettighetstypeperiodeRepository.lagre(
            avsluttetBehandling.behandlingsReferanse,
            avsluttetBehandling.rettighetstypeperioder
        )

        if (!skjermingService.erSkjermet(uthentetBehandling)) {
            ytelsesStatistikkTilBigQuery.lagre(uthentetBehandling.referanse)
        } else {
            logger.info("Lagrer ikke i BigQuery fordi noen i saken er skjermet.")
        }
        meterRegistry.avsluttetBehandlingLagret().increment()
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