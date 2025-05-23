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
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository

class LagreStoppetHendelseJobb(
    private val meterRegistry: MeterRegistry,
    private val tilkjentYtelseRepositoryFactory: (DBConnection) -> ITilkjentYtelseRepository,
    private val beregningsgrunnlagRepositoryFactory: (DBConnection) -> IBeregningsgrunnlagRepository,
    private val vilkårsResultatRepositoryFactory: (DBConnection) -> IVilkårsresultatRepository,
    private val diagnoseRepository: (DBConnection) -> DiagnoseRepository,
    private val behandlingRepositoryFactory: (DBConnection) -> IBehandlingRepository,
    private val rettighetstypeperiodeRepository: (DBConnection) -> IRettighetstypeperiodeRepository,
    private val personService: (DBConnection) -> PersonService,
    private val skjermingService: SkjermingService,
    private val jobbAppender: JobbAppender
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        val hendelsesService = HendelsesService(
            sakRepository = SakRepositoryImpl(connection),
            avsluttetBehandlingService = AvsluttetBehandlingService(
                tilkjentYtelseRepository = tilkjentYtelseRepositoryFactory(connection),
                beregningsgrunnlagRepository = beregningsgrunnlagRepositoryFactory(connection),
                vilkårsResultatRepository = vilkårsResultatRepositoryFactory(connection),
                diagnoseRepository = diagnoseRepository(connection),
                behandlingRepository = behandlingRepositoryFactory(connection),
                skjermingService = skjermingService,
                meterRegistry = meterRegistry,
                rettighetstypeperiodeRepository = rettighetstypeperiodeRepository(connection),
                opprettBigQueryLagringYtelseCallback = { behandlingId ->
                    jobbAppender.leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
                        connection,
                        behandlingId
                    )
                }
            ),
            personService = personService(connection),
            behandlingRepository = BehandlingRepository(connection),
            meterRegistry = meterRegistry,
            opprettBigQueryLagringSakStatistikkCallback = { behandlingId ->
                jobbAppender.leggTilLagreSakTilBigQueryJobb(
                    connection,
                    behandlingId
                )
            },
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