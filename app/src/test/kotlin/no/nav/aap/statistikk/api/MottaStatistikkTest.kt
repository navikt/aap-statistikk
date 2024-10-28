package no.nav.aap.statistikk.api

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.Motor
import no.nav.aap.motor.mdc.NoExtraLogInfoProvider
import no.nav.aap.statistikk.api_kontrakt.*
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.db.FellesKomponentTransactionalExecutor
import no.nav.aap.statistikk.hendelseLagret
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.sak.BigQueryKvitteringRepository
import no.nav.aap.statistikk.sak.SakRepositoryImpl
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

        val bqRepository = FakeBQRepository()
        val meterRegistry = SimpleMeterRegistry()

        val avsluttetBehandlingCounter = meterRegistry.avsluttetBehandlingLagret()
        val stoppetHendelseLagretCounter = meterRegistry.hendelseLagret()

        val mottattTid = LocalDateTime.now().minusDays(1)
        testKlient(
            noOpTransactionExecutor,
            motor,
            jobbAppender,
            azureConfig,
            LagreStoppetHendelseJobb(
                bqRepository, stoppetHendelseLagretCounter,
                bigQueryKvitteringRepository = { FakeBigQueryKvitteringRepository() },
                tilkjentYtelseRepositoryFactory = { FakeTilkjentYtelseRepository() },
                beregningsgrunnlagRepositoryFactory = { FakeBeregningsgrunnlagRepository() },
                vilkårsResultatRepositoryFactory = { FakeVilkårsResultatRepository() },
                behandlingRepositoryFactory = { FakeBehandlingRepository() },
                avsluttetBehandlingLagretCounter = avsluttetBehandlingCounter,
                skjermingService = SkjermingService(FakePdlClient())
            )
        ) { url, client ->
            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"), PostRequest(
                    StoppetBehandling(
                        saksnummer = "123",
                        status = BehandlingStatus.OPPRETTET,
                        behandlingType = TypeBehandling.Førstegangsbehandling,
                        ident = "0",
                        behandlingReferanse = behandlingReferanse,
                        behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                        avklaringsbehov = listOf(),
                        versjon = "UKJENT",
                        mottattTid = mottattTid,
                        sakStatus = SakStatus.UTREDES,
                        hendelsesTidspunkt = hendelsesTidspunkt
                    )
                )
            )
        }

        assertThat(jobbAppender.jobber.first().payload()).isEqualTo(
            DefaultJsonMapper.toJson(
                StoppetBehandling(
                    saksnummer = "123",
                    status = BehandlingStatus.OPPRETTET,
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    ident = "0",
                    behandlingReferanse = behandlingReferanse,
                    behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                    avklaringsbehov = listOf(),
                    versjon = "UKJENT",
                    mottattTid = mottattTid,
                    sakStatus = SakStatus.UTREDES,
                    hendelsesTidspunkt = hendelsesTidspunkt
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
            status = BehandlingStatus.UTREDES,
            behandlingType = TypeBehandling.Førstegangsbehandling,
            ident = "14890097570",
            avklaringsbehov = listOf(
                AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = "5003",
                        behovType = BehovType.valueOf("MANUELT_PÅKREVD"),
                        løsesISteg = StegType.AVKLAR_SYKDOM
                    ),
                    status = EndringStatus.valueOf("SENDT_TILBAKE_FRA_KVALITETSSIKRER"),
                    endringer = listOf(
                        Endring(
                            status = EndringStatus.valueOf("OPPRETTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T10:35:34.842"),
                            frist = null,
                            endretAv = "Kelvin"
                        ), Endring(
                            status = EndringStatus.valueOf("AVSLUTTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:50:50.217"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                ), AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = "5006",
                        behovType = BehovType.valueOf("MANUELT_PÅKREVD"),
                        løsesISteg = StegType.VURDER_BISTANDSBEHOV
                    ),
                    status = EndringStatus.valueOf("SENDT_TILBAKE_FRA_KVALITETSSIKRER"),
                    endringer = listOf(
                        Endring(
                            status = EndringStatus.valueOf("OPPRETTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:50:52.049"),
                            frist = null,
                            endretAv = "Kelvin"
                        ), Endring(
                            status = EndringStatus.valueOf("AVSLUTTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:51:16.176"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                ), AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = "5097",
                        behovType = BehovType.valueOf("MANUELT_PÅKREVD"),
                        løsesISteg = StegType.KVALITETSSIKRING
                    ), status = EndringStatus.valueOf("AVSLUTTET"), endringer = listOf(
                        Endring(
                            status = EndringStatus.valueOf("OPPRETTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:51:17.231"),
                            frist = null,
                            endretAv = "Kelvin"
                        ), Endring(
                            status = EndringStatus.valueOf("AVSLUTTET"),
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
            hendelsesTidspunkt = LocalDateTime.now()
        )

        val hendelse2 = hendelse.copy(hendelsesTidspunkt = LocalDateTime.now().plusSeconds(0))

        val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)

        val bqRepository = FakeBQRepository()
        val meterRegistry = SimpleMeterRegistry()

        val stoppetHendelseLagretCounter = meterRegistry.hendelseLagret()
        val avsluttetBehandlingCounter = meterRegistry.avsluttetBehandlingLagret()

        val skjermingService = SkjermingService(FakePdlClient())

        val lagreStoppetHendelseJobb = LagreStoppetHendelseJobb(
            bqRepository, stoppetHendelseLagretCounter,
            bigQueryKvitteringRepository = { BigQueryKvitteringRepository(it) },
            tilkjentYtelseRepositoryFactory = { TilkjentYtelseRepository(it) },
            beregningsgrunnlagRepositoryFactory = { BeregningsgrunnlagRepository(it) },
            vilkårsResultatRepositoryFactory = { VilkårsresultatRepository(it) },
            behandlingRepositoryFactory = { BehandlingRepository(it) },
            avsluttetBehandlingLagretCounter = avsluttetBehandlingCounter,
            skjermingService = skjermingService
        )

        val motor = Motor(
            dataSource = dataSource,
            antallKammer = 8,
            logInfoProvider = NoExtraLogInfoProvider,
            jobber = listOf(
                lagreStoppetHendelseJobb
            )
        )

        val jobbAppender = MotorJobbAppender(dataSource)

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
            lagreStoppetHendelseJobb
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
                ventPåSvar({
                    SakRepositoryImpl(
                        it
                    ).tellSaker()
                },
                    { it?.let { it > 0 } ?: false })
            }
        }

        val exceptions = listAppender.list.filter { it.throwableProxy != null }
        assertThat(exceptions).isEmpty()

        motor.stop()
    }

    @Test
    fun `kan motta mer avansert object`(
        @Postgres dataSource: DataSource, @Fakes azureConfig: AzureConfig
    ) {
        val hendelse = StoppetBehandling(
            saksnummer = "4LFK2S0",
            behandlingReferanse = UUID.fromString("96175156-0950-475a-8de0-41a25f4c0cec"),
            status = BehandlingStatus.UTREDES,
            behandlingType = TypeBehandling.Førstegangsbehandling,
            ident = "14890097570",
            avklaringsbehov = listOf(
                AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = "5003",
                        behovType = BehovType.valueOf("MANUELT_PÅKREVD"),
                        løsesISteg = StegType.AVKLAR_SYKDOM
                    ),
                    status = EndringStatus.valueOf("SENDT_TILBAKE_FRA_KVALITETSSIKRER"),
                    endringer = listOf(
                        Endring(
                            status = EndringStatus.valueOf("OPPRETTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T10:35:34.842"),
                            frist = null,
                            endretAv = "Kelvin"
                        ), Endring(
                            status = EndringStatus.valueOf("AVSLUTTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:50:50.217"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                ), AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = "5006",
                        behovType = BehovType.valueOf("MANUELT_PÅKREVD"),
                        løsesISteg = StegType.VURDER_BISTANDSBEHOV
                    ),
                    status = EndringStatus.valueOf("SENDT_TILBAKE_FRA_KVALITETSSIKRER"),
                    endringer = listOf(
                        Endring(
                            status = EndringStatus.valueOf("OPPRETTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:50:52.049"),
                            frist = null,
                            endretAv = "Kelvin"
                        ), Endring(
                            status = EndringStatus.valueOf("AVSLUTTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:51:16.176"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                ), AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = "5097",
                        behovType = BehovType.valueOf("MANUELT_PÅKREVD"),
                        løsesISteg = StegType.KVALITETSSIKRING
                    ), status = EndringStatus.valueOf("AVSLUTTET"), endringer = listOf(
                        Endring(
                            status = EndringStatus.valueOf("OPPRETTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:51:17.231"),
                            frist = null,
                            endretAv = "Kelvin"
                        ), Endring(
                            status = EndringStatus.valueOf("AVSLUTTET"),
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
            hendelsesTidspunkt = LocalDateTime.now()
        )

        val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)

        val bqRepository = FakeBQRepository()
        val meterRegistry = SimpleMeterRegistry()

        val stoppetHendelseLagretCounter = meterRegistry.hendelseLagret()
        val avsluttetBehandlingCounter = meterRegistry.avsluttetBehandlingLagret()

        val skjermingService = SkjermingService(FakePdlClient())

        val lagreStoppetHendelseJobb = LagreStoppetHendelseJobb(
            bqRepository, stoppetHendelseLagretCounter,
            bigQueryKvitteringRepository = { BigQueryKvitteringRepository(it) },
            tilkjentYtelseRepositoryFactory = { TilkjentYtelseRepository(it) },
            beregningsgrunnlagRepositoryFactory = { BeregningsgrunnlagRepository(it) },
            vilkårsResultatRepositoryFactory = { VilkårsresultatRepository(it) },
            behandlingRepositoryFactory = { BehandlingRepository(it) },
            avsluttetBehandlingLagretCounter = avsluttetBehandlingCounter,
            skjermingService = skjermingService
        )
        val motor = Motor(
            dataSource = dataSource,
            antallKammer = 2,
            logInfoProvider = NoExtraLogInfoProvider,
            jobber = listOf(lagreStoppetHendelseJobb)
        )

        val jobbAppender = MotorJobbAppender(dataSource)

        testKlient(
            transactionExecutor,
            motor,
            jobbAppender,
            azureConfig,
            lagreStoppetHendelseJobb
        ) { url, client ->

            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"),
                PostRequest(hendelse)
            )

            dataSource.transaction(readOnly = true) {
                ventPåSvar({
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
            val uthentetSak = hendelsesRepository.hentSak(hendelse.saksnummer)
            val uthentetBehandling = BehandlingRepository(it).hent(hendelse.behandlingReferanse)

            assertThat(uthentetBehandling?.referanse).isEqualTo(hendelse.behandlingReferanse)
            assertThat(uthentetSak.saksnummer).isEqualTo(hendelse.saksnummer)
            assertThat(uthentetBehandling?.sak?.saksnummer).isEqualTo(hendelse.saksnummer)
            assertThat(uthentetBehandling?.opprettetTid).isEqualTo(
                hendelse.behandlingOpprettetTidspunkt
            )
            assertThat(uthentetBehandling?.typeBehandling).isEqualTo(hendelse.behandlingType)
            assertThat(uthentetBehandling?.status).isEqualTo(hendelse.status)

            assertThat(avsluttetBehandlingCounter.count()).isEqualTo(0.0)
            assertThat(stoppetHendelseLagretCounter.count()).isEqualTo(1.0)
        }

        motor.stop()
    }
}