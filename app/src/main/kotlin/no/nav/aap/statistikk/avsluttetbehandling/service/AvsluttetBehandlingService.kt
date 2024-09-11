package no.nav.aap.statistikk.avsluttetbehandling.service

import no.nav.aap.statistikk.Factory
import no.nav.aap.statistikk.TransactionExecutor
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository

class AvsluttetBehandlingService(
    private val transactionExecutor: TransactionExecutor,
    private val tilkjentYtelseRepositoryFactory: Factory<ITilkjentYtelseRepository>,
    private val beregningsgrunnlagRepositoryFactory: Factory<BeregningsgrunnlagRepository>,
    private val vilkårsResultatRepository: VilkårsresultatRepository,
    private val bqRepository: IBQRepository
) {
    fun lagre(avsluttetBehandling: AvsluttetBehandling) {
        vilkårsResultatRepository.lagreVilkårsResultat(
            VilkårsResultatEntity.fraDomene(avsluttetBehandling.vilkårsresultat)
        )

        transactionExecutor.withinTransaction {
            tilkjentYtelseRepositoryFactory.create(it).lagreTilkjentYtelse(
                TilkjentYtelseEntity.fraDomene(
                    avsluttetBehandling.tilkjentYtelse
                )
            )

            beregningsgrunnlagRepositoryFactory.create(it).lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    value = avsluttetBehandling.beregningsgrunnlag,
                    behandlingsReferanse = avsluttetBehandling.behandlingsReferanse
                )
            )
        }

        bqRepository.lagre(avsluttetBehandling.vilkårsresultat)
        bqRepository.lagre(avsluttetBehandling.tilkjentYtelse)
        bqRepository.lagre(
            avsluttetBehandling.beregningsgrunnlag,
            avsluttetBehandling.behandlingsReferanse
        )
    }
}