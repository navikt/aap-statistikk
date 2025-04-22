package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.behandling.DiagnoseRepository
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.IBQYtelsesstatistikkRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository
import java.util.*

class LagreAvsluttetBehandlingTilBigQueryJobbUtfører(private val ytelsesStatistikkTilBigQuery: YtelsesStatistikkTilBigQuery) :
    JobbUtfører {
    override fun utfør(input: JobbInput) {
        val behandlingReferanse = input.payload<UUID>()
        ytelsesStatistikkTilBigQuery.lagre(behandlingReferanse)
    }
}

class LagreAvsluttetBehandlingTilBigQueryJobb(
    private val behandlingRepositoryFactory: (DBConnection) -> IBehandlingRepository,
    private val rettighetstypeperiodeRepositoryFactory: (DBConnection) -> IRettighetstypeperiodeRepository,
    private val diagnoseRepositoryFactory: (DBConnection) -> DiagnoseRepository,
    private val vilkårsResulatRepositoryFactory: (DBConnection) -> IVilkårsresultatRepository,
    private val tilkjentYtelseRepositoryFactory: (DBConnection) -> ITilkjentYtelseRepository,
    private val beregningsgrunnlagRepositoryFactory: (DBConnection) -> IBeregningsgrunnlagRepository,
    private val bqRepository: IBQYtelsesstatistikkRepository
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        return LagreAvsluttetBehandlingTilBigQueryJobbUtfører(
            YtelsesStatistikkTilBigQuery(
                bqRepository = bqRepository,
                behandlingRepository = behandlingRepositoryFactory(connection),
                rettighetstypeperiodeRepository = rettighetstypeperiodeRepositoryFactory(connection),
                diagnoseRepository = diagnoseRepositoryFactory(connection),
                vilkårsresultatRepository = vilkårsResulatRepositoryFactory(connection),
                tilkjentYtelseRepository = tilkjentYtelseRepositoryFactory(connection),
                beregningsgrunnlagRepository = beregningsgrunnlagRepositoryFactory(connection),
            )
        )
    }

    override fun type(): String {
        return "statistikk.lagreAvsluttetBehandlingTilBigQueryJobb"
    }

    override fun navn(): String {
        return "lagreAvsluttetBehandlingTilBigQuery"
    }

    override fun beskrivelse(): String {
        return "Lagrer avsluttet behandling til BigQuery (til Team Spenn)."
    }

    override fun retries(): Int {
        return 1
    }
}