package no.nav.aap.statistikk.jobber

import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.avsluttetbehandling.IRettighetstypeperiodeRepository
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.DiagnoseRepository
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.IBQSakstatistikkRepository
import no.nav.aap.statistikk.bigquery.IBQYtelsesstatistikkRepository
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.hendelser.SaksStatistikkService
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.IBigQueryKvitteringRepository
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository

class LagreStoppetHendelseJobb(
    private val bqYtelseStatistikk: IBQYtelsesstatistikkRepository,
    private val bqSakstatikk: IBQSakstatistikkRepository,
    private val meterRegistry: MeterRegistry,
    private val bigQueryKvitteringRepository: (DBConnection) -> IBigQueryKvitteringRepository,
    private val tilkjentYtelseRepositoryFactory: (DBConnection) -> ITilkjentYtelseRepository,
    private val beregningsgrunnlagRepositoryFactory: (DBConnection) -> IBeregningsgrunnlagRepository,
    private val vilkårsResultatRepositoryFactory: (DBConnection) -> IVilkårsresultatRepository,
    private val diagnoseRepository: (DBConnection) -> DiagnoseRepository,
    private val behandlingRepositoryFactory: (DBConnection) -> IBehandlingRepository,
    private val rettighetstypeperiodeRepository: (DBConnection) -> IRettighetstypeperiodeRepository,
    private val personService: (DBConnection) -> PersonService,
    private val skjermingService: SkjermingService,
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        val hendelsesService = HendelsesService(
            sakRepository = SakRepositoryImpl(connection),
            avsluttetBehandlingService = AvsluttetBehandlingService(
                tilkjentYtelseRepository = tilkjentYtelseRepositoryFactory(connection),
                beregningsgrunnlagRepository = beregningsgrunnlagRepositoryFactory(connection),
                vilkårsResultatRepository = vilkårsResultatRepositoryFactory(connection),
                diagnoseRepository = diagnoseRepository(connection),
                bqRepository = bqYtelseStatistikk,
                behandlingRepository = behandlingRepositoryFactory(connection),
                skjermingService = skjermingService,
                meterRegistry = meterRegistry,
                rettighetstypeperiodeRepository = rettighetstypeperiodeRepository(connection),
            ),
            personService = personService(connection),
            behandlingRepository = BehandlingRepository(connection),
            meterRegistry = meterRegistry,
            sakStatistikkService = SaksStatistikkService(
                behandlingRepository = behandlingRepositoryFactory(connection),
                bigQueryKvitteringRepository = bigQueryKvitteringRepository(connection),
                bigQueryRepository = bqSakstatikk,
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