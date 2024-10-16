package no.nav.aap.statistikk.jobber

import io.micrometer.core.instrument.Counter
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingRepository
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.db.FellesKomponentConnectionExecutor
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository

class LagreAvsluttetBehandlingJobbKonstruktør(
    private val bQRepository: IBQRepository,
    private val avsluttetBehandlingCounter: Counter,
    private val tilkjentYtelseRepository: (DBConnection) -> TilkjentYtelseRepository = {
        TilkjentYtelseRepository(
            it
        )
    },
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        val avsluttetBehandlingService = AvsluttetBehandlingService(
            transactionExecutor = FellesKomponentConnectionExecutor(connection),
            tilkjentYtelseRepositoryFactory = tilkjentYtelseRepository,
            beregningsgrunnlagRepositoryFactory = { BeregningsgrunnlagRepository(connection) },
            vilkårsResultatRepositoryFactory = { VilkårsresultatRepository(it) },
            bqRepository = bQRepository,
            behandlingRepositoryFactory = { BehandlingRepository(it) }
        )
        return LagreAvsluttetBehandlingPostgresJobbUtfører(
            avsluttetBehandlingService,
            AvsluttetBehandlingRepository(connection),
            avsluttetBehandlingCounter
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