package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.behandling.BQYtelseBehandling
import no.nav.aap.statistikk.behandling.Behandling
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
    private val tilkjentYtelseRepositoryFactory: (DBConnection) -> ITilkjentYtelseRepository,
    private val beregningsgrunnlagRepositoryFactory: (DBConnection) -> IBeregningsgrunnlagRepository,
    private val vilkårsResultatRepositoryFactory: (DBConnection) -> IVilkårsresultatRepository,
    private val bqRepository: IBQRepository,
    private val behandlingRepositoryFactory: (DBConnection) -> IBehandlingRepository
) {
    fun lagre(avsluttetBehandling: AvsluttetBehandling) {
        transactionExecutor.withinTransaction {

            val uthentetBehandling =
                behandlingRepositoryFactory(it).hent(avsluttetBehandling.behandlingsReferanse)

            if (uthentetBehandling != null) {
                vilkårsResultatRepositoryFactory(it)
                    .lagreVilkårsResultat(
                        VilkårsResultatEntity.fraDomene(avsluttetBehandling.vilkårsresultat),
                        uthentetBehandling.id!!
                    )
            } else {
                error("Ingen behandling med referanse ${avsluttetBehandling.behandlingsReferanse}.")
            }

            tilkjentYtelseRepositoryFactory(it).lagreTilkjentYtelse(
                TilkjentYtelseEntity.fraDomene(
                    avsluttetBehandling.tilkjentYtelse
                )
            )

            if (avsluttetBehandling.beregningsgrunnlag != null) {
                beregningsgrunnlagRepositoryFactory(it).lagreBeregningsGrunnlag(
                    MedBehandlingsreferanse(
                        value = avsluttetBehandling.beregningsgrunnlag,
                        behandlingsReferanse = avsluttetBehandling.behandlingsReferanse
                    )
                )
            }

            lagreAvsluttetBehandlingIBigQuery(avsluttetBehandling, uthentetBehandling)

        }
    }

    private fun lagreAvsluttetBehandlingIBigQuery(
        avsluttetBehandling: AvsluttetBehandling,
        behandling: Behandling
    ) {
        bqRepository.lagre(
            BQYtelseBehandling(
                referanse = avsluttetBehandling.behandlingsReferanse,
                brukerFnr = behandling.sak.person.ident,
                behandlingsType = behandling.typeBehandling
            )
        )
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