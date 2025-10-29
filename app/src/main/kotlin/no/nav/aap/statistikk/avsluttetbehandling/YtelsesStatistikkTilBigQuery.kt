package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsGrunnlagBQ
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.IBQYtelsesstatistikkRepository
import no.nav.aap.statistikk.hendelser.onlyOrNull
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

class YtelsesStatistikkTilBigQuery(
    private val bqRepository: IBQYtelsesstatistikkRepository,
    private val behandlingRepository: IBehandlingRepository,
    private val rettighetstypeperiodeRepository: IRettighetstypeperiodeRepository,
    private val diagnoseRepository: DiagnoseRepository,
    private val vilkårsresultatRepository: IVilkårsresultatRepository,
    private val tilkjentYtelseRepository: ITilkjentYtelseRepository,
    private val beregningsgrunnlagRepository: IBeregningsgrunnlagRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    companion object {
        fun konstruer(
            connection: DBConnection,
            bqRepository: IBQYtelsesstatistikkRepository,
            repositoryProvider: RepositoryProvider
        ): YtelsesStatistikkTilBigQuery {
            return YtelsesStatistikkTilBigQuery(
                bqRepository = bqRepository,
                behandlingRepository = repositoryProvider.provide(),
                rettighetstypeperiodeRepository = RettighetstypeperiodeRepository(connection),
                diagnoseRepository = DiagnoseRepositoryImpl(connection),
                vilkårsresultatRepository = VilkårsresultatRepository(connection),
                tilkjentYtelseRepository = TilkjentYtelseRepository(connection),
                beregningsgrunnlagRepository = BeregningsgrunnlagRepository(connection),
            )
        }
    }

    fun lagre(behandlingReferanse: UUID) {
        val behandling =
            requireNotNull(behandlingRepository.hent(behandlingReferanse)) { "Prøver å lagre en ikke-eksisterende behandling med referanse $behandlingReferanse" }
        val rettighetstypeperioder = rettighetstypeperiodeRepository.hent(behandling.referanse)
        val diagnoser = diagnoseRepository.hentForBehandling(behandling.referanse)

        val vilkårsResultat =
            vilkårsresultatRepository.hentForBehandling(behandling.referanse).tilVilkårsResultat(
                behandling.sak.saksnummer,
                behandling.referanse,
                behandling.typeBehandling.name
            )

        val tilkjentYtelse = tilkjentYtelseRepository.hentForBehandling(behandling.referanse)

        val utbetalingId = behandling.utbetalingId()

        bqRepository.start()
        bqRepository.lagre(
            BQYtelseBehandling(
                saksnummer = behandling.sak.saksnummer,
                referanse = behandling.referanse,
                utbetalingId = behandling.utbetalingId(),
                brukerFnr = behandling.sak.person.ident,
                resultat = behandling.resultat(),
                behandlingsType = behandling.typeBehandling,
                datoOpprettet = behandling.opprettetTid,
                datoAvsluttet = behandling.avsluttetTid(),
                kodeverk = diagnoser?.kodeverk,
                diagnosekode = diagnoser?.diagnosekode,
                bidiagnoser = diagnoser?.bidiagnoser,
                rettighetsPerioder = rettighetstypeperioder,
                radEndret = LocalDateTime.now(clock),
                vurderingsbehov = behandling.årsaker.map { it.name }
            )
        )
        bqRepository.lagre(vilkårsResultat)
        if (tilkjentYtelse != null) {
            bqRepository.lagre(tilkjentYtelse)
        }

        val beregningsGrunnlag =
            beregningsgrunnlagRepository.hentBeregningsGrunnlag(behandling.referanse)
                .onlyOrNull()

        val tilGrunnlag = beregningsGrunnlag?.let {
            tilBqGrunnlag(
                behandling.referanse,
                behandling.sak.saksnummer,
                it.value,
            )
        }

        if (tilGrunnlag != null) {
            bqRepository.lagre(tilGrunnlag)
        }
        bqRepository.commit()
    }

    private fun tilBqGrunnlag(
        behandlingsreferanse: UUID,
        saksnummer: Saksnummer,
        value: IBeregningsGrunnlag
    ): BeregningsGrunnlagBQ {
        return when (value) {
            is IBeregningsGrunnlag.GrunnlagUføre -> BeregningsGrunnlagBQ(
                saksnummer = saksnummer.value,
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
                    saksnummer = saksnummer.value,
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
                saksnummer = saksnummer.value,
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