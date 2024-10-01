package no.nav.aap.statistikk.avsluttetbehandling.service

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.Factory
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.GrunnlagType
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsGrunnlagBQ
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import java.util.*

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
                tilBqGrunnlag(
                    avsluttetBehandling.behandlingsReferanse,
                    avsluttetBehandling.saksnummer,
                    avsluttetBehandling.beregningsgrunnlag
                )
            )
        }
    }

    private fun tilBqGrunnlag(
        behandlingsreferanse: UUID,
        saksnummer: String,
        value: IBeregningsGrunnlag
    ): BeregningsGrunnlagBQ {
        return when (value) {
            is IBeregningsGrunnlag.GrunnlagUføre -> BeregningsGrunnlagBQ(
                saksnummer = saksnummer,
                behandlingsreferanse = behandlingsreferanse,
                type = GrunnlagType.Grunnlag_Ufore,
                grunnlaget = value.grunnlag,
                standardGrunnlag = value.grunnlag11_19.grunnlag,
                standardEr6GBegrenset = value.grunnlag11_19.er6GBegrenset,
                standardErGjennomsnitt = value.grunnlag11_19.erGjennomsnitt,
                uføreGrunnlag = value.grunnlag,
                uføreUføregrad = value.uføregrad,
            )

            is IBeregningsGrunnlag.GrunnlagYrkesskade -> {
                val grunnlagUføre = when (value.beregningsgrunnlag) {
                    is IBeregningsGrunnlag.GrunnlagUføre -> value.beregningsgrunnlag
                    else -> null
                }

                val grunnlag1119 = when (value.beregningsgrunnlag) {
                    is IBeregningsGrunnlag.Grunnlag_11_19 -> value.beregningsgrunnlag
                    is IBeregningsGrunnlag.GrunnlagUføre -> value.beregningsgrunnlag.grunnlag11_19
                    else -> error("Må ha 11-19 som basegrunnlag")
                }

                BeregningsGrunnlagBQ(
                    saksnummer = saksnummer,
                    behandlingsreferanse = behandlingsreferanse,
                    type = GrunnlagType.GrunnlagYrkesskade,
                    grunnlaget = value.grunnlaget,
                    standardGrunnlag = grunnlag1119.grunnlag,
                    standardEr6GBegrenset = grunnlag1119.er6GBegrenset,
                    standardErGjennomsnitt = grunnlag1119.erGjennomsnitt,
                    uføreGrunnlag = grunnlagUføre?.grunnlag,
                    uføreUføregrad = grunnlagUføre?.uføregrad,
                    yrkesskadeTerskelVerdiForYrkesskade = value.terskelverdiForYrkesskade,
                    yrkesskadeAndelSomSkyldesYrkesskade = value.andelSomSkyldesYrkesskade.toDouble(),
                    yrkesskadeAndelSomIkkeSkyldesYrkesskade = value.andelSomIkkeSkyldesYrkesskade.toDouble(),
                    yrkesskadeAndelYrkesskade = value.andelYrkesskade,
                    yrkesskadeBenyttetAndelForYrkesskade = value.benyttetAndelForYrkesskade,
                    yrkesskadeAntattÅrligInntektYrkesskadeTidspunktet = value.antattÅrligInntektYrkesskadeTidspunktet.toDouble(),
                    yrkesskadeYrkesskadeTidspunkt = value.yrkesskadeTidspunkt,
                    yrkesskadeGrunnlagForBeregningAvYrkesskadeandel = value.grunnlagForBeregningAvYrkesskadeandel.toDouble(),
                    yrkesskadeYrkesskadeinntektIG = value.yrkesskadeinntektIG.toDouble(),
                    yrkesskadeGrunnlagEtterYrkesskadeFordel = value.grunnlagEtterYrkesskadeFordel.toDouble(),
                )
            }

            is IBeregningsGrunnlag.Grunnlag_11_19 -> BeregningsGrunnlagBQ(
                saksnummer = saksnummer,
                behandlingsreferanse = behandlingsreferanse,
                type = GrunnlagType.Grunnlag11_19,
                grunnlaget = value.grunnlag,
                standardGrunnlag = value.grunnlag,
                standardEr6GBegrenset = value.er6GBegrenset,
                standardErGjennomsnitt = value.erGjennomsnitt,
            )
        }
    }
}