package no.nav.aap.statistikk.avsluttetbehandling.service

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.Factory
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity

class AvsluttetBehandlingService(
    private val transactionExecutor: TransactionExecutor,
    private val tilkjentYtelseRepositoryFactory: Factory<ITilkjentYtelseRepository>,
    private val beregningsgrunnlagRepositoryFactory: Factory<IBeregningsgrunnlagRepository>,
    private val vilkårsResultatRepositoryFactory: Factory<IVilkårsresultatRepository>,
    private val bqRepository: IBQRepository,
    private val behandlingRepositoryFactory: (DBConnection) -> IBehandlingRepository
) {
    fun lagre(avsluttetBehandling: AvsluttetBehandling) {
        transactionExecutor.withinTransaction {

            val uthentetBehandling =
                behandlingRepositoryFactory(it).hent(avsluttetBehandling.behandlingsReferanse)
            if (uthentetBehandling != null) {
                vilkårsResultatRepositoryFactory.create(it)
                    .lagreVilkårsResultat(
                        VilkårsResultatEntity.fraDomene(avsluttetBehandling.vilkårsresultat),
                        uthentetBehandling.id!!
                    )
            } else {
                error("Ingen behandling med referanse ${avsluttetBehandling.behandlingsReferanse}.")
            }
            tilkjentYtelseRepositoryFactory.create(it).lagreTilkjentYtelse(
                TilkjentYtelseEntity.fraDomene(
                    avsluttetBehandling.tilkjentYtelse
                )
            )

            if (avsluttetBehandling.beregningsgrunnlag != null) {
                beregningsgrunnlagRepositoryFactory.create(it).lagreBeregningsGrunnlag(
                    MedBehandlingsreferanse(
                        value = avsluttetBehandling.beregningsgrunnlag,
                        behandlingsReferanse = avsluttetBehandling.behandlingsReferanse
                    )
                )
            }

        }

        bqRepository.lagre(avsluttetBehandling.vilkårsresultat)
        bqRepository.lagre(avsluttetBehandling.tilkjentYtelse)
        if (avsluttetBehandling.beregningsgrunnlag != null) {
            bqRepository.lagre(
                avsluttetBehandling.beregningsgrunnlag,
                avsluttetBehandling.behandlingsReferanse
            )
        }
    }
}