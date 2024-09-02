package no.nav.aap.statistikk.avsluttetbehandling.service

import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.beregningsgrunnlag.BeregningsGrunnlagService
import no.nav.aap.statistikk.bigquery.BQRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseEntity
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.VilkårsResultatService

class AvsluttetBehandlingService(
    private val vilkårsResultatService: VilkårsResultatService,
    private val tilkjentYtelseRepository: TilkjentYtelseRepository,
    private val beregningsGrunnlagService: BeregningsGrunnlagService,
    private val bqRepository: BQRepository
) {
    fun lagre(avsluttetBehandling: AvsluttetBehandling) {
        vilkårsResultatService.mottaVilkårsResultat(
            avsluttetBehandling.vilkårsresultat
        )

        tilkjentYtelseRepository.lagreTilkjentYtelse(
            TilkjentYtelseEntity.fraDomene(
                avsluttetBehandling.tilkjentYtelse
            )
        )

        beregningsGrunnlagService.mottaBeregningsGrunnlag(
            MedBehandlingsreferanse(
                value = avsluttetBehandling.beregningsgrunnlag,
                behandlingsReferanse = avsluttetBehandling.behandlingsReferanse
            )
        )

        bqRepository.lagre(avsluttetBehandling.vilkårsresultat)
        bqRepository.lagre(avsluttetBehandling.tilkjentYtelse)
        bqRepository.lagre(avsluttetBehandling.beregningsgrunnlag)

    }
}