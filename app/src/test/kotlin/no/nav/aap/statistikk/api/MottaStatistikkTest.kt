package no.nav.aap.statistikk.api

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.*
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.Motor
import no.nav.aap.motor.mdc.NoExtraLogInfoProvider
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.postmottak.kontrakt.journalpost.JournalpostId
import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.saksstatistikk.LagreSakinfoTilBigQueryJobb
import no.nav.aap.statistikk.db.FellesKomponentTransactionalExecutor
import no.nav.aap.statistikk.hendelseLagret
import no.nav.aap.statistikk.hendelser.tilDomene
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import no.nav.aap.statistikk.lagretPostmottakHendelse
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobb
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.postmottak.LagrePostmottakHendelseJobb
import no.nav.aap.statistikk.postmottak.PostmottakBehandlingRepository
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.sak.SakService
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.*
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource
import no.nav.aap.behandlingsflyt.kontrakt.sak.Status as SakStatus


@Fakes
class MottaStatistikkTest {
    @Test
    fun `hendelse blir lagret i repository`(
        @Fakes azureConfig: AzureConfig
    ) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingOpprettetTidspunkt = LocalDateTime.now()
        val hendelsesTidspunkt = LocalDateTime.now()
        val jobbAppender = MockJobbAppender()

        val motor = motorMock()

        val meterRegistry = SimpleMeterRegistry()

        val mottattTid = LocalDateTime.now().minusDays(1)
        testKlient(
            noOpTransactionExecutor,
            motor,
            jobbAppender,
            azureConfig,
            LagreStoppetHendelseJobb(
                meterRegistry,
                tilkjentYtelseRepositoryFactory = { FakeTilkjentYtelseRepository() },
                beregningsgrunnlagRepositoryFactory = { FakeBeregningsgrunnlagRepository() },
                vilkårsResultatRepositoryFactory = { FakeVilkårsResultatRepository() },
                diagnoseRepository = { FakeDiagnoseRepository() },
                sakService = { SakService(FakeSakRepository()) },
                behandlingRepositoryFactory = { FakeBehandlingRepository() },
                rettighetstypeperiodeRepository = { FakeRettighetsTypeRepository() },
                personService = { PersonService(FakePersonRepository()) },
                skjermingService = SkjermingService(FakePdlClient()),
                jobbAppender = jobbAppender,
            ),
            LagreOppgaveHendelseJobb(meterRegistry, jobbAppender),
            LagrePostmottakHendelseJobb(meterRegistry)
        ) { url, client ->
            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"), PostRequest(
                    StoppetBehandling(
                        saksnummer = "123",
                        behandlingStatus = Status.OPPRETTET,
                        behandlingType = TypeBehandling.Førstegangsbehandling,
                        ident = "0",
                        behandlingReferanse = behandlingReferanse,
                        behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                        avklaringsbehov = listOf(),
                        versjon = "UKJENT",
                        mottattTid = mottattTid,
                        sakStatus = SakStatus.UTREDES,
                        hendelsesTidspunkt = hendelsesTidspunkt,
                        vurderingsbehov = listOf(Vurderingsbehov.SØKNAD),
                    )
                )
            )
        }

        assertThat(jobbAppender.jobber.first().payload()).isEqualTo(
            DefaultJsonMapper.toJson(
                StoppetBehandling(
                    saksnummer = "123",
                    behandlingStatus = Status.OPPRETTET,
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    ident = "0",
                    behandlingReferanse = behandlingReferanse,
                    behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                    avklaringsbehov = listOf(),
                    versjon = "UKJENT",
                    mottattTid = mottattTid,
                    sakStatus = SakStatus.UTREDES,
                    hendelsesTidspunkt = hendelsesTidspunkt,
                    vurderingsbehov = listOf(Vurderingsbehov.SØKNAD)
                )
            )
        )
    }

    @RepeatedTest(value = 2)
    fun `motta to events rett etter hverandre`(
        @Postgres dataSource: DataSource,
        @Fakes azureConfig: AzureConfig
    ) {
        val hendelse = StoppetBehandling(
            saksnummer = "4LFK2S0",
            behandlingReferanse = UUID.fromString("96175156-0950-475a-8de0-41a25f4c0cec"),
            behandlingStatus = Status.OPPRETTET,
            behandlingType = TypeBehandling.Førstegangsbehandling,
            ident = "14890097570",
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = AVKLAR_SYKDOM,
                    status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                    endringer = listOf(
                        EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                            tidsstempel = LocalDateTime.parse("2024-08-14T10:35:34.842"),
                            frist = null,
                            endretAv = "Kelvin"
                        ), EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:50:50.217"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                ), AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = AVKLAR_BISTANDSBEHOV,
                    status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                    endringer = listOf(
                        EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:50:52.049"),
                            frist = null,
                            endretAv = KELVIN
                        ), EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:51:16.176"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                ), AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = KVALITETSSIKRING,
                    status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                    endringer = listOf(
                        EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:51:17.231"),
                            frist = null,
                            endretAv = "Kelvin"
                        ), EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:54:22.268"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                )
            ),
            behandlingOpprettetTidspunkt = LocalDateTime.parse("2024-08-14T10:35:33.595"),
            versjon = "UKJENT",
            mottattTid = LocalDateTime.now().minusDays(1),
            sakStatus = SakStatus.UTREDES,
            hendelsesTidspunkt = LocalDateTime.now(),
            vurderingsbehov = listOf(Vurderingsbehov.SØKNAD)
        )

        val hendelse2 = hendelse.copy(hendelsesTidspunkt = LocalDateTime.now().plusSeconds(0))

        val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)

        val bqRepositoryYtelse = FakeBQYtelseRepository()
        val bqStatistikkRepository = FakeBQSakRepository()
        val meterRegistry = SimpleMeterRegistry()

        val skjermingService = SkjermingService(FakePdlClient())

        val jobbAppender1 = MockJobbAppender()
        val lagreStoppetHendelseJobb = LagreStoppetHendelseJobb(
            meterRegistry,
            tilkjentYtelseRepositoryFactory = { TilkjentYtelseRepository(it) },
            beregningsgrunnlagRepositoryFactory = { BeregningsgrunnlagRepository(it) },
            vilkårsResultatRepositoryFactory = { VilkårsresultatRepository(it) },
            diagnoseRepository = { FakeDiagnoseRepository() },
            sakService = { SakService(SakRepositoryImpl(it)) },
            behandlingRepositoryFactory = { BehandlingRepository(it) },
            rettighetstypeperiodeRepository = { FakeRettighetsTypeRepository() },
            personService = { PersonService(PersonRepository(it)) },
            skjermingService = skjermingService,
            jobbAppender = jobbAppender1
        )

        val lagreOppgaveHendelseJobb = LagreOppgaveHendelseJobb(meterRegistry, jobbAppender1)
        val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb(meterRegistry)

        val lagreSakinfoTilBigQueryJobb = LagreSakinfoTilBigQueryJobb(
            bigQueryKvitteringRepository = { FakeBigQueryKvitteringRepository() },
            behandlingRepositoryFactory = { FakeBehandlingRepository() },
            bqSakstatikk = bqStatistikkRepository,
            skjermingService = skjermingService
        )

        val lagreAvsluttetBehandlingTilBigQueryJobb = LagreAvsluttetBehandlingTilBigQueryJobb(
            behandlingRepositoryFactory = { FakeBehandlingRepository() },
            rettighetstypeperiodeRepositoryFactory = { FakeRettighetsTypeRepository() },
            diagnoseRepositoryFactory = { FakeDiagnoseRepository() },
            vilkårsResulatRepositoryFactory = { FakeVilkårsResultatRepository() },
            tilkjentYtelseRepositoryFactory = { FakeTilkjentYtelseRepository() },
            beregningsgrunnlagRepositoryFactory = { FakeBeregningsgrunnlagRepository() },
            bqRepository = bqRepositoryYtelse,
        )

        val motor = opprettMotor(
            dataSource,
            lagreStoppetHendelseJobb,
            lagreSakinfoTilBigQueryJobb,
            lagrePostmottakHendelseJobb
        )

        val jobbAppender =
            MotorJobbAppender(lagreSakinfoTilBigQueryJobb, lagreAvsluttetBehandlingTilBigQueryJobb)

        val logger =
            LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val listAppender = ListAppender<ILoggingEvent>()

        listAppender.start()

        logger.addAppender(listAppender)


        testKlient(
            transactionExecutor,
            motor,
            jobbAppender,
            azureConfig,
            lagreStoppetHendelseJobb, lagreOppgaveHendelseJobb, lagrePostmottakHendelseJobb
        ) { url, client ->

            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"),
                PostRequest(hendelse)
            )

            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"),
                PostRequest(hendelse2)
            )

            dataSource.transaction(readOnly = true) {
                ventPåSvar(
                    {
                        SakRepositoryImpl(
                            it
                        ).tellSaker()
                    },
                    { it?.let { it > 0 } ?: false })
            }

            val exceptions = listAppender.list.filter { it.throwableProxy != null }
            assertThat(exceptions).isEmpty()
            motor.stop()
        }
    }

    private fun opprettMotor(
        dataSource: DataSource,
        lagreStoppetHendelseJobb: LagreStoppetHendelseJobb,
        lagreSakinfoTilBigQueryJobb: LagreSakinfoTilBigQueryJobb,
        lagrePostmottakHendelseJobb: LagrePostmottakHendelseJobb
    ) = Motor(
        dataSource = dataSource,
        antallKammer = 8,
        logInfoProvider = NoExtraLogInfoProvider,
        jobber = listOf(
            lagreStoppetHendelseJobb, lagreSakinfoTilBigQueryJobb, lagrePostmottakHendelseJobb
        )
    )

    @Test
    fun `kan motta mer avansert object`(
        @Postgres dataSource: DataSource, @Fakes azureConfig: AzureConfig
    ) {
        val hendelse = StoppetBehandling(
            saksnummer = "4LFK2S0",
            behandlingReferanse = UUID.fromString("96175156-0950-475a-8de0-41a25f4c0cec"),
            behandlingStatus = Status.OPPRETTET,
            behandlingType = TypeBehandling.Førstegangsbehandling,
            ident = "14890097570",
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = AVKLAR_SYKDOM,
                    status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                    endringer = listOf(
                        EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                            tidsstempel = LocalDateTime.parse("2024-08-14T10:35:34.842"),
                            frist = null,
                            endretAv = "Kelvin"
                        ), EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:50:50.217"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                ), AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = AVKLAR_BISTANDSBEHOV,
                    status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                    endringer = listOf(
                        EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:50:52.049"),
                            frist = null,
                            endretAv = "Kelvin"
                        ), EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:51:16.176"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                ), AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = KVALITETSSIKRING,
                    status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                    endringer = listOf(
                        EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:51:17.231"),
                            frist = null,
                            endretAv = "Kelvin"
                        ), EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:54:22.268"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                )
            ),
            behandlingOpprettetTidspunkt = LocalDateTime.parse("2024-08-14T10:35:33.595"),
            versjon = "UKJENT",
            mottattTid = LocalDateTime.now().minusDays(1),
            sakStatus = SakStatus.UTREDES,
            hendelsesTidspunkt = LocalDateTime.now(),
            vurderingsbehov = listOf(Vurderingsbehov.SØKNAD)
        )

        val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)

        val bqRepositoryYtelse = FakeBQYtelseRepository()
        val bqStatistikkRepository = FakeBQSakRepository()
        val meterRegistry = SimpleMeterRegistry()

        val stoppetHendelseLagretCounter = meterRegistry.hendelseLagret()
        val avsluttetBehandlingCounter = meterRegistry.avsluttetBehandlingLagret()

        val skjermingService = SkjermingService(FakePdlClient())

        val jobbAppender1 = MockJobbAppender()
        val lagreStoppetHendelseJobb = LagreStoppetHendelseJobb(
            meterRegistry,
            tilkjentYtelseRepositoryFactory = { TilkjentYtelseRepository(it) },
            beregningsgrunnlagRepositoryFactory = { BeregningsgrunnlagRepository(it) },
            vilkårsResultatRepositoryFactory = { VilkårsresultatRepository(it) },
            diagnoseRepository = { FakeDiagnoseRepository() },
            sakService = { SakService(SakRepositoryImpl(it)) },
            behandlingRepositoryFactory = { BehandlingRepository(it) },
            rettighetstypeperiodeRepository = { FakeRettighetsTypeRepository() },
            personService = { PersonService(PersonRepository(it)) },
            skjermingService = skjermingService,
            jobbAppender = jobbAppender1
        )

        val lagreOppgaveHendelseJobb = LagreOppgaveHendelseJobb(meterRegistry, jobbAppender1)
        val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb(meterRegistry)

        val lagreSakinfoTilBigQueryJobb = LagreSakinfoTilBigQueryJobb(
            bigQueryKvitteringRepository = { FakeBigQueryKvitteringRepository() },
            behandlingRepositoryFactory = { FakeBehandlingRepository() },
            bqSakstatikk = bqStatistikkRepository,
            skjermingService = skjermingService
        )

        val lagreAvsluttetBehandlingTilBigQueryJobb = LagreAvsluttetBehandlingTilBigQueryJobb(
            behandlingRepositoryFactory = { FakeBehandlingRepository() },
            rettighetstypeperiodeRepositoryFactory = { FakeRettighetsTypeRepository() },
            diagnoseRepositoryFactory = { FakeDiagnoseRepository() },
            vilkårsResulatRepositoryFactory = { FakeVilkårsResultatRepository() },
            tilkjentYtelseRepositoryFactory = { FakeTilkjentYtelseRepository() },
            beregningsgrunnlagRepositoryFactory = { FakeBeregningsgrunnlagRepository() },
            bqRepository = bqRepositoryYtelse,
        )


        val motor = opprettMotor(
            dataSource,
            lagreStoppetHendelseJobb,
            lagreSakinfoTilBigQueryJobb,
            lagrePostmottakHendelseJobb
        )


        val jobbAppender =
            MotorJobbAppender(lagreSakinfoTilBigQueryJobb, lagreAvsluttetBehandlingTilBigQueryJobb)

        testKlient(
            transactionExecutor,
            motor,
            jobbAppender,
            azureConfig,
            lagreStoppetHendelseJobb, lagreOppgaveHendelseJobb, lagrePostmottakHendelseJobb
        ) { url, client ->

            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"),
                PostRequest(hendelse)
            )

            dataSource.transaction(readOnly = true) {
                ventPåSvar(
                    {
                        SakRepositoryImpl(
                            it
                        ).tellSaker()
                    },
                    { it?.let { it > 0 } ?: false })
            }
        }

        dataSource.transaction {
            val hendelsesRepository = SakRepositoryImpl(
                it
            )
            val uthentetSak = hendelsesRepository.hentSak(hendelse.saksnummer.let(::Saksnummer))
            val uthentetBehandling = BehandlingRepository(it).hent(hendelse.behandlingReferanse)

            assertThat(uthentetBehandling?.referanse).isEqualTo(hendelse.behandlingReferanse)
            assertThat(uthentetBehandling?.gjeldendeAvklaringsBehov).isEqualTo("5006")
            assertThat(uthentetSak.saksnummer.value).isEqualTo(hendelse.saksnummer)
            assertThat(uthentetBehandling?.sak?.saksnummer!!.value).isEqualTo(hendelse.saksnummer)
            assertThat(uthentetBehandling.opprettetTid).isEqualTo(
                hendelse.behandlingOpprettetTidspunkt
            )
            assertThat(uthentetBehandling.typeBehandling).isEqualTo(hendelse.behandlingType.tilDomene())
            assertThat(uthentetBehandling.status).isEqualTo(hendelse.behandlingStatus.tilDomene())

            assertThat(avsluttetBehandlingCounter.count()).isEqualTo(0.0)
            assertThat(stoppetHendelseLagretCounter.count()).isEqualTo(1.0)
        }

        motor.stop()
    }

    @Test
    fun `kan motta postmottak-hendelse, og jobb blir utført`(
        @Postgres dataSource: DataSource,
        @Fakes azureConfig: AzureConfig
    ) {
        val referanse = UUID.randomUUID()
        val hendelse = DokumentflytStoppetHendelse(
            journalpostId = JournalpostId(23),
            ident = "323",
            referanse = referanse,
            behandlingType = no.nav.aap.postmottak.kontrakt.behandling.TypeBehandling.Journalføring,
            status = no.nav.aap.postmottak.kontrakt.behandling.Status.OPPRETTET,
            avklaringsbehov = listOf(),
            opprettetTidspunkt = LocalDateTime.now(),
            hendelsesTidspunkt = LocalDateTime.now(),
            saksnummer = null
        )
        val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)

        val bqRepositoryYtelse = FakeBQYtelseRepository()
        val bqRepositorySak = FakeBQSakRepository()

        val meterRegistry = SimpleMeterRegistry()

        val stoppetHendelseLagretCounter = meterRegistry.hendelseLagret()
        val avsluttetBehandlingCounter = meterRegistry.avsluttetBehandlingLagret()

        val skjermingService = SkjermingService(FakePdlClient())

        val jobbAppender1 = MockJobbAppender()
        val lagreStoppetHendelseJobb = LagreStoppetHendelseJobb(
            meterRegistry,
            tilkjentYtelseRepositoryFactory = { TilkjentYtelseRepository(it) },
            beregningsgrunnlagRepositoryFactory = { BeregningsgrunnlagRepository(it) },
            vilkårsResultatRepositoryFactory = { VilkårsresultatRepository(it) },
            diagnoseRepository = { FakeDiagnoseRepository() },
            sakService = { SakService(SakRepositoryImpl(it)) },
            behandlingRepositoryFactory = { BehandlingRepository(it) },
            rettighetstypeperiodeRepository = { FakeRettighetsTypeRepository() },
            personService = { PersonService(PersonRepository(it)) },
            skjermingService = skjermingService,
            jobbAppender = jobbAppender1
        )

        val lagreOppgaveHendelseJobb = LagreOppgaveHendelseJobb(meterRegistry, jobbAppender1)
        val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb(meterRegistry)

        val lagreSakinfoTilBigQueryJobb = LagreSakinfoTilBigQueryJobb(
            bigQueryKvitteringRepository = { FakeBigQueryKvitteringRepository() },
            behandlingRepositoryFactory = { FakeBehandlingRepository() },
            bqSakstatikk = bqRepositorySak,
            skjermingService = skjermingService
        )

        val lagreAvsluttetBehandlingTilBigQueryJobb = LagreAvsluttetBehandlingTilBigQueryJobb(
            behandlingRepositoryFactory = { FakeBehandlingRepository() },
            rettighetstypeperiodeRepositoryFactory = { FakeRettighetsTypeRepository() },
            diagnoseRepositoryFactory = { FakeDiagnoseRepository() },
            vilkårsResulatRepositoryFactory = { FakeVilkårsResultatRepository() },
            tilkjentYtelseRepositoryFactory = { FakeTilkjentYtelseRepository() },
            beregningsgrunnlagRepositoryFactory = { FakeBeregningsgrunnlagRepository() },
            bqRepository = bqRepositoryYtelse,
        )

        val motor = opprettMotor(
            dataSource,
            lagreStoppetHendelseJobb,
            lagreSakinfoTilBigQueryJobb,
            lagrePostmottakHendelseJobb
        )

        val jobbAppender =
            MotorJobbAppender(lagreSakinfoTilBigQueryJobb, lagreAvsluttetBehandlingTilBigQueryJobb)

        testKlient(
            transactionExecutor,
            motor,
            jobbAppender,
            azureConfig,
            lagreStoppetHendelseJobb, lagreOppgaveHendelseJobb, lagrePostmottakHendelseJobb
        ) { url, client ->

            client.post<DokumentflytStoppetHendelse, Any>(
                URI.create("$url/postmottak"),
                PostRequest(hendelse)
            )

            dataSource.transaction(readOnly = true) {
                ventPåSvar(
                    {
                        PostmottakBehandlingRepository(
                            it
                        ).hentEksisterendeBehandling(referanse)
                    },
                    { it != null })
            }
        }

        dataSource.transaction {
            assertThat(avsluttetBehandlingCounter.count()).isEqualTo(0.0)
            assertThat(stoppetHendelseLagretCounter.count()).isEqualTo(0.0)
            assertThat(meterRegistry.lagretPostmottakHendelse().count()).isEqualTo(1.0)
        }

        motor.stop()
    }
}