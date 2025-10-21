package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseEntity
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import org.slf4j.LoggerFactory

class AvsluttetBehandlingService(
    private val tilkjentYtelseRepository: ITilkjentYtelseRepository,
    private val beregningsgrunnlagRepository: IBeregningsgrunnlagRepository,
    private val vilkårsResultatRepository: IVilkårsresultatRepository,
    private val diagnoseRepository: DiagnoseRepository,
    private val behandlingRepository: IBehandlingRepository,
    private val rettighetstypeperiodeRepository: IRettighetstypeperiodeRepository,
    private val skjermingService: SkjermingService,
    private val opprettBigQueryLagringYtelseCallback: (BehandlingId) -> Unit,
) {
    private val logger = LoggerFactory.getLogger(AvsluttetBehandlingService::class.java)

    companion object {
        fun konstruer(
            dbConnection: DBConnection,
            skjermingService: SkjermingService,
            opprettBigQueryLagringYtelseCallback: (BehandlingId) -> Unit,
        ) = AvsluttetBehandlingService(
            tilkjentYtelseRepository = TilkjentYtelseRepository(dbConnection),
            beregningsgrunnlagRepository = BeregningsgrunnlagRepository(dbConnection),
            vilkårsResultatRepository = VilkårsresultatRepository(dbConnection),
            diagnoseRepository = DiagnoseRepositoryImpl(dbConnection),
            behandlingRepository = BehandlingRepository(dbConnection),
            rettighetstypeperiodeRepository = RettighetstypeperiodeRepository(dbConnection),
            skjermingService = skjermingService,
            opprettBigQueryLagringYtelseCallback = opprettBigQueryLagringYtelseCallback
        )
    }

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
            opprettBigQueryLagringYtelseCallback(uthentetBehandling.id)
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