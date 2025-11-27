package no.nav.aap.statistikk.api

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
import no.nav.aap.motor.testutil.TestUtil
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.postmottak.kontrakt.journalpost.JournalpostId
import no.nav.aap.statistikk.*
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.bigquery.IBQYtelsesstatistikkRepository
import no.nav.aap.statistikk.db.FellesKomponentTransactionalExecutor
import no.nav.aap.statistikk.hendelser.tilDomene
import no.nav.aap.statistikk.jobber.LagreAvklaringsbehovHendelseJobb
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobb
import no.nav.aap.statistikk.oppgave.LagreOppgaveJobb
import no.nav.aap.statistikk.postmottak.LagrePostmottakHendelseJobb
import no.nav.aap.statistikk.postmottak.PostmottakBehandlingRepositoryImpl
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.saksstatistikk.LagreSakinfoTilBigQueryJobb
import no.nav.aap.statistikk.saksstatistikk.ResendSakstatistikkJobb
import no.nav.aap.statistikk.saksstatistikk.SakstatistikkRepository
import no.nav.aap.statistikk.testutils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
        val mottattTid = LocalDateTime.now().minusDays(1)
        val jobbAppender = MockJobbAppender()

        val motor = motorMock()

        val testHendelse = opprettTestStoppetBehandling(
            behandlingReferanse, behandlingOpprettetTidspunkt, hendelsesTidspunkt, mottattTid
        )

        testKlient(
            noOpTransactionExecutor,
            motor,
            azureConfig,
            LagreStoppetHendelseJobb(jobbAppender, defaultGatewayProvider { }),
            LagreOppgaveHendelseJobb(LagreOppgaveJobb()),
            LagrePostmottakHendelseJobb(),
            LagreAvklaringsbehovHendelseJobb(jobbAppender),
            jobbAppender,
        ) { url, client ->
            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"), PostRequest(testHendelse)
            )
        }

        assertThat(jobbAppender.jobber.first().payload()).isEqualTo(
            DefaultJsonMapper.toJson(
                testHendelse
            )
        )
    }

    private fun ekteLagreStoppetHendelseJobb(
        jobbAppender: JobbAppender,
    ): LagreStoppetHendelseJobb = LagreStoppetHendelseJobb(jobbAppender, defaultGatewayProvider { })

    private val hendelse = StoppetBehandling(
        saksnummer = "4LFK2S0",
        behandlingReferanse = UUID.fromString("96175156-0950-475a-8de0-41a25f4c0cec"),
        behandlingStatus = Status.IVERKSETTES,
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
                        endretAv = "Z9945111"
                    )
                )
            ), AvklaringsbehovHendelseDto(
                avklaringsbehovDefinisjon = AVKLAR_BISTANDSBEHOV,
                status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
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
                        endretAv = "Z994500"
                    )
                )
            ), AvklaringsbehovHendelseDto(
                avklaringsbehovDefinisjon = SKRIV_VEDTAKSBREV,
                status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                endringer = listOf(
                    EndringDTO(
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        tidsstempel = LocalDateTime.parse("2024-08-15T11:51:17.231"),
                        frist = null,
                        endretAv = "Kelvin"
                    )
                )
            )
        ),
        behandlingOpprettetTidspunkt = LocalDateTime.parse("2024-08-14T10:35:33.595"),
        versjon = "UKJENT",
        mottattTid = LocalDateTime.parse("2024-08-14T10:33:33.595").minusDays(1),
        sakStatus = SakStatus.UTREDES,
        hendelsesTidspunkt = LocalDateTime.now(),
        vurderingsbehov = listOf(Vurderingsbehov.SØKNAD)
    )

    @Test
    fun `regenerere historikk for behandling`(
        @Postgres dataSource: DataSource, @Fakes azureConfig: AzureConfig
    ) {
        val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)
        val testJobber = konstruerTestJobber()

        val lagreOppgaveHendelseJobb = LagreOppgaveHendelseJobb(LagreOppgaveJobb())
        val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb()
        val lagreAvklaringsbehovHendelseJobb =
            LagreAvklaringsbehovHendelseJobb(testJobber.motorJobbAppender)

        val motor = konstruerMotor(
            dataSource,
            testJobber.motorJobbAppender,
            FakeBQYtelseRepository(),
            testJobber.resendSakstatistikkJobb,
            lagreAvklaringsbehovHendelseJobb,
            lagrePostmottakHendelseJobb,
            testJobber.lagreSakinfoTilBigQueryJobb
        )

        testKlient(
            transactionExecutor,
            motor,
            azureConfig,
            ekteLagreStoppetHendelseJobb(testJobber.motorJobbAppender),
            lagreOppgaveHendelseJobb,
            lagrePostmottakHendelseJobb,
            lagreAvklaringsbehovHendelseJobb,
            testJobber.motorJobbAppender,
        ) { url, client ->

            client.post<StoppetBehandling, Any>(
                URI.create("$url/oppdatertBehandling"), PostRequest(hendelse)
            )


            TestUtil(dataSource, listOf("oppgave.retryFeilede")).ventPåSvar()

            val (behandling, bqBehandlinger) = dataSource.transaction {
                val behandling = BehandlingRepository(it).hent(hendelse.behandlingReferanse)!!
                Pair(
                    behandling,
                    it.provider().provide<SakstatistikkRepository>()
                        .hentAlleHendelserPåBehandling(behandling.referanse)
                )
            }
            assertThat(behandling.hendelser.sortedBy { it.hendelsesTidspunkt }).hasSize(5)
            assertThat(bqBehandlinger).hasSize(5)
        }
    }


    @Test
    fun `kan motta mer avansert object`(
        @Postgres dataSource: DataSource, @Fakes azureConfig: AzureConfig
    ) {
        val meterRegistry = SimpleMeterRegistry()
        PrometheusProvider.prometheus = meterRegistry
        val stoppetHendelseLagretCounter = meterRegistry.hendelseLagret()
        val avsluttetBehandlingCounter = meterRegistry.avsluttetBehandlingLagret()

        val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)
        val testJobber = konstruerTestJobber()

        val lagreOppgaveHendelseJobb = LagreOppgaveHendelseJobb(LagreOppgaveJobb())
        val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb()
        val lagreAvklaringsbehovHendelseJobb =
            LagreAvklaringsbehovHendelseJobb(testJobber.motorJobbAppender)

        val motor = konstruerMotor(
            dataSource,
            testJobber.motorJobbAppender,
            FakeBQYtelseRepository(),
            testJobber.resendSakstatistikkJobb,
            lagreAvklaringsbehovHendelseJobb,
            lagrePostmottakHendelseJobb,
            testJobber.lagreSakinfoTilBigQueryJobb
        )

        testKlient(
            transactionExecutor,
            motor,
            azureConfig,
            ekteLagreStoppetHendelseJobb(testJobber.motorJobbAppender),
            lagreOppgaveHendelseJobb,
            lagrePostmottakHendelseJobb,
            lagreAvklaringsbehovHendelseJobb,
            testJobber.motorJobbAppender,
        ) { url, client ->

            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"), PostRequest(hendelse)
            )

            dataSource.transaction(readOnly = true) {
                ventPåSvar({
                    SakRepositoryImpl(
                        it
                    ).tellSaker()
                }, { it?.let { it > 0 } ?: false })
            }
        }

        val (uthentetSak, uthentetBehandling) = dataSource.transaction {
            val hendelsesRepository = SakRepositoryImpl(
                it
            )
            val uthentetSak = hendelsesRepository.hentSak(hendelse.saksnummer.let(::Saksnummer))
            val uthentetBehandling = BehandlingRepository(it).hent(hendelse.behandlingReferanse)
            Pair(uthentetSak, uthentetBehandling)
        }

        assertThat(uthentetBehandling?.referanse).isEqualTo(hendelse.behandlingReferanse)
        assertThat(uthentetBehandling?.gjeldendeAvklaringsBehov).isEqualTo("5051")
        assertThat(uthentetSak.saksnummer.value).isEqualTo(hendelse.saksnummer)
        assertThat(uthentetBehandling?.sak?.saksnummer!!.value).isEqualTo(hendelse.saksnummer)
        assertThat(uthentetBehandling.opprettetTid).isEqualTo(
            hendelse.behandlingOpprettetTidspunkt
        )
        assertThat(uthentetBehandling.typeBehandling).isEqualTo(hendelse.behandlingType.tilDomene())
        assertThat(uthentetBehandling.status).isEqualTo(hendelse.behandlingStatus.tilDomene())

        assertThat(avsluttetBehandlingCounter.count()).isEqualTo(0.0)
        assertThat(stoppetHendelseLagretCounter.count()).isEqualTo(1.0)

        motor.stop()
    }

    private fun konstruerMotor(
        dataSource: DataSource,
        jobbAppender: MotorJobbAppender,
        bqYtelseRepository: IBQYtelsesstatistikkRepository,
        resendSakstatistikkJobb: ResendSakstatistikkJobb,
        lagreAvklaringsbehovHendelseJobb: LagreAvklaringsbehovHendelseJobb,
        lagrePostmottakHendelseJobb: LagrePostmottakHendelseJobb,
        lagreSakinfoTilBigQueryJobb: LagreSakinfoTilBigQueryJobb
    ): Motor {
        val lagreOppgaveJobb = LagreOppgaveJobb()
        return motor(
            dataSource = dataSource,
            jobber = listOf(
                LagreAvsluttetBehandlingTilBigQueryJobb(bqYtelseRepository),
                lagreOppgaveJobb,
                resendSakstatistikkJobb,
                lagreAvklaringsbehovHendelseJobb,
                lagrePostmottakHendelseJobb,
                LagreOppgaveHendelseJobb(lagreOppgaveJobb),
                lagreSakinfoTilBigQueryJobb,
                LagreStoppetHendelseJobb(jobbAppender, defaultGatewayProvider { })
            )
        )
    }

    @Test
    fun `kan motta postmottak-hendelse, og jobb blir utført`(
        @Postgres dataSource: DataSource, @Fakes azureConfig: AzureConfig
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

        val meterRegistry = SimpleMeterRegistry()
        PrometheusProvider.prometheus = meterRegistry
        val stoppetHendelseLagretCounter = meterRegistry.hendelseLagret()
        val avsluttetBehandlingCounter = meterRegistry.avsluttetBehandlingLagret()

        val jobbAppender1 = MockJobbAppender()
        val lagreStoppetHendelseJobb = ekteLagreStoppetHendelseJobb(jobbAppender1)

        val lagreOppgaveJobb = LagreOppgaveJobb()
        val lagreOppgaveHendelseJobb = LagreOppgaveHendelseJobb(lagreOppgaveJobb)
        val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb()

        val lagreSakinfoTilBigQueryJobb = LagreSakinfoTilBigQueryJobb(
        )

        val lagreAvsluttetBehandlingTilBigQueryJobb =
            LagreAvsluttetBehandlingTilBigQueryJobb(FakeBQYtelseRepository())

        val resendSakstatistikkJobb = ResendSakstatistikkJobb()
        val motor = motor(
            dataSource = dataSource,
            jobber = listOf(
                resendSakstatistikkJobb,
                lagreAvsluttetBehandlingTilBigQueryJobb,
                lagreSakinfoTilBigQueryJobb,
                LagreAvklaringsbehovHendelseJobb(jobbAppender1),
                lagrePostmottakHendelseJobb,
                lagreOppgaveHendelseJobb,
                lagreOppgaveJobb,
                lagreStoppetHendelseJobb
            )
        )
        val jobbAppender = MotorJobbAppender(
            lagreSakinfoTilBigQueryJobb,
            lagreAvsluttetBehandlingTilBigQueryJobb,
            resendSakstatistikkJobb,
        )

        testKlient(
            transactionExecutor,
            motor,
            azureConfig,
            lagreStoppetHendelseJobb,
            lagreOppgaveHendelseJobb,
            lagrePostmottakHendelseJobb,
            LagreAvklaringsbehovHendelseJobb(jobbAppender),
            jobbAppender,
        ) { url, client ->

            client.post<DokumentflytStoppetHendelse, Any>(
                URI.create("$url/postmottak"), PostRequest(hendelse)
            )

            dataSource.transaction(readOnly = true) {
                ventPåSvar({
                    PostmottakBehandlingRepositoryImpl(
                        it
                    ).hentEksisterendeBehandling(referanse)
                }, { it != null })
            }
        }

        dataSource.transaction {
            assertThat(avsluttetBehandlingCounter.count()).isEqualTo(0.0)
            assertThat(stoppetHendelseLagretCounter.count()).isEqualTo(0.0)
            assertThat(meterRegistry.lagretPostmottakHendelse().count()).isEqualTo(1.0)
        }

        motor.stop()
    }

    private fun konstruerTestJobber(
        bqYtelseRepository: IBQYtelsesstatistikkRepository = FakeBQYtelseRepository()
    ): TestJobberSetup {
        val lagreSakinfoTilBigQueryJobb = LagreSakinfoTilBigQueryJobb()
        val lagreAvsluttetBehandlingTilBigQueryJobb =
            LagreAvsluttetBehandlingTilBigQueryJobb(bqYtelseRepository)
        val resendSakstatistikkJobb = ResendSakstatistikkJobb()
        val motorJobbAppender = MotorJobbAppender(
            lagreSakinfoTilBigQueryJobb,
            lagreAvsluttetBehandlingTilBigQueryJobb,
            resendSakstatistikkJobb,
        )
        return TestJobberSetup(
            lagreSakinfoTilBigQueryJobb,
            lagreAvsluttetBehandlingTilBigQueryJobb,
            resendSakstatistikkJobb,
            motorJobbAppender
        )
    }

    private fun opprettTestStoppetBehandling(
        behandlingReferanse: UUID,
        behandlingOpprettetTidspunkt: LocalDateTime,
        hendelsesTidspunkt: LocalDateTime,
        mottattTid: LocalDateTime,
        saksnummer: String = "123",
        behandlingStatus: Status = Status.OPPRETTET,
        behandlingType: TypeBehandling = TypeBehandling.Førstegangsbehandling,
        ident: String = "0",
        avklaringsbehov: List<AvklaringsbehovHendelseDto> = listOf()
    ) = StoppetBehandling(
        saksnummer = saksnummer,
        behandlingStatus = behandlingStatus,
        behandlingType = behandlingType,
        ident = ident,
        behandlingReferanse = behandlingReferanse,
        behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
        avklaringsbehov = avklaringsbehov,
        versjon = "UKJENT",
        mottattTid = mottattTid,
        sakStatus = SakStatus.UTREDES,
        hendelsesTidspunkt = hendelsesTidspunkt,
        vurderingsbehov = listOf(Vurderingsbehov.SØKNAD)
    )

    private data class TestJobberSetup(
        val lagreSakinfoTilBigQueryJobb: LagreSakinfoTilBigQueryJobb,
        val lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb,
        val resendSakstatistikkJobb: ResendSakstatistikkJobb,
        val motorJobbAppender: MotorJobbAppender
    )
}
