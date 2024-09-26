package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.Factory
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingRepository
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.db.FellesKomponentConnectionExecutor
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository

class LagreAvsluttetBehandlingJobbKonstruktør(
    private val bQRepository: IBQRepository
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        val avsluttetBehandlingService = AvsluttetBehandlingService(
            transactionExecutor = FellesKomponentConnectionExecutor(connection),
            tilkjentYtelseRepositoryFactory = object : Factory<TilkjentYtelseRepository> {
                override fun create(dbConnection: DBConnection) =
                    TilkjentYtelseRepository(connection)
            },
            beregningsgrunnlagRepositoryFactory = object :
                Factory<BeregningsgrunnlagRepository> {
                override fun create(dbConnection: DBConnection): BeregningsgrunnlagRepository {
                    return BeregningsgrunnlagRepository(connection)
                }
            },
            vilkårsResultatRepositoryFactory = object : Factory<VilkårsresultatRepository> {
                override fun create(dbConnection: DBConnection): VilkårsresultatRepository {
                    return VilkårsresultatRepository(connection)
                }
            },
            bqRepository = bQRepository,
            behandlingRepositoryFactory = { BehandlingRepository(it) }
        )
        return LagreAvsluttetBehandlingPostgresJobbUtfører(
            avsluttetBehandlingService,
            AvsluttetBehandlingRepository(connection)
        )
    }

    override fun type(): String {
        return "prosesserAvsluttetBehandling"
    }

    override fun navn(): String {
        return "Prosesser avsluttet behandling"
    }

    override fun beskrivelse(): String {
        return "Prosesser avsluttet behandling"
    }
}