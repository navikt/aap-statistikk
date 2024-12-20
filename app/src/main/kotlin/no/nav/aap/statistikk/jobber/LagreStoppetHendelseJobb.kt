package no.nav.aap.statistikk.jobber

import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.hendelser.SaksStatistikkService
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.sak.IBigQueryKvitteringRepository
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository

class LagreStoppetHendelseJobb(
    private val bqRepository: IBQRepository,
    private val meterRegistry: MeterRegistry,
    private val bigQueryKvitteringRepository: (DBConnection) -> IBigQueryKvitteringRepository,
    private val tilkjentYtelseRepositoryFactory: (DBConnection) -> ITilkjentYtelseRepository,
    private val beregningsgrunnlagRepositoryFactory: (DBConnection) -> IBeregningsgrunnlagRepository,
    private val vilkårsResultatRepositoryFactory: (DBConnection) -> IVilkårsresultatRepository,
    private val behandlingRepositoryFactory: (DBConnection) -> IBehandlingRepository,
    private val skjermingService: SkjermingService,
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        val hendelsesService = HendelsesService(
            sakRepository = SakRepositoryImpl(connection),
            avsluttetBehandlingService = AvsluttetBehandlingService(
                tilkjentYtelseRepositoryFactory = tilkjentYtelseRepositoryFactory(connection),
                beregningsgrunnlagRepositoryFactory = beregningsgrunnlagRepositoryFactory(connection),
                vilkårsResultatRepositoryFactory = vilkårsResultatRepositoryFactory(connection),
                bqRepository = bqRepository,
                behandlingRepository = behandlingRepositoryFactory(connection),
                skjermingService = skjermingService,
                meterRegistry = meterRegistry,
            ),
            personRepository = PersonRepository(connection),
            behandlingRepository = BehandlingRepository(connection),
            meterRegistry = meterRegistry,
            sakStatistikkService = SaksStatistikkService(
                behandlingRepository = behandlingRepositoryFactory(connection),
                bigQueryKvitteringRepository = bigQueryKvitteringRepository(connection),
                bigQueryRepository = bqRepository,
                skjermingService = skjermingService,
            ),
        )
        return LagreStoppetHendelseJobbUtfører(
            hendelsesService,
        )
    }

    override fun type(): String {
        return "statistikk.lagreHendelse"
    }

    override fun navn(): String {
        return "lagreHendelse"
    }

    override fun beskrivelse(): String {
        return "beskrivelse"
    }
}