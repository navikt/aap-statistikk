package no.nav.aap.statistikk.api

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.*
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.behandling.ÅrsakTilOpprettelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.TilbakekrevingsbehandlingOppdatertHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.dokumenter.TilbakekrevingBehandlingsstatus
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.*
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.testutil.ManuellMotorImpl
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.postmottak.kontrakt.journalpost.JournalpostId
import no.nav.aap.statistikk.*
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.db.FellesKomponentTransactionalExecutor
import no.nav.aap.statistikk.hendelser.tilDomene
import no.nav.aap.statistikk.jobber.LagreAvklaringsbehovHendelseJobb
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobb
import no.nav.aap.statistikk.oppgave.LagreOppgaveJobb
import no.nav.aap.statistikk.postmottak.LagrePostmottakHendelseJobb
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.saksstatistikk.LagreSakinfoTilBigQueryJobb
import no.nav.aap.statistikk.saksstatistikk.ResendSakstatistikkJobb
import no.nav.aap.statistikk.saksstatistikk.SakstatistikkRepository
import no.nav.aap.statistikk.testutils.*
import no.nav.aap.statistikk.tilbakekreving.TilbakekrevingBehandlingStatus
import no.nav.aap.statistikk.tilbakekreving.TilbakekrevingHendelse
import no.nav.aap.statistikk.tilbakekreving.TilbakekrevingHendelseRepositoryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
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
            LagreStoppetHendelseJobb(jobbAppender, mockk()),
            jobbAppender,
        ) {
            postBehandlingsflytHendelse(testHendelse)
        }

        assertThat(jobbAppender.jobber.first().payload()).isEqualTo(
            DefaultJsonMapper.toJson(
                testHendelse
            )
        )
    }

    private fun ekteLagreStoppetHendelseJobb(
        jobbAppender: JobbAppender,
    ): LagreStoppetHendelseJobb = LagreStoppetHendelseJobb(jobbAppender, mockk())

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
        vurderingsbehov = listOf(Vurderingsbehov.SØKNAD),
        årsakTilOpprettelse = ÅrsakTilOpprettelse.SØKNAD
    )

    @Test
    fun `regenerere historikk for behandling`(
        @Postgres dataSource: DataSource, @Fakes azureConfig: AzureConfig
    ) {
        val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)
        val testJobber = konstruerTestJobber()

        val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb()
        val lagreAvklaringsbehovHendelseJobb =
            LagreAvklaringsbehovHendelseJobb(testJobber.motorJobbAppender)

        val motor = konstruerManuellMotor(
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
            testJobber.motorJobbAppender,
        ) {

            oppdatertBehandlingHendelse(hendelse)

            motor.kjørJobber()

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

    private val meldekorthendelse = StoppetBehandling(
        saksnummer = "4LFK2S0",
        behandlingReferanse = UUID.fromString("96175156-0950-475a-8de0-41a25f4c0cec"),
        behandlingStatus = Status.AVSLUTTET,
        behandlingType = TypeBehandling.Revurdering,
        ident = "14890097570",
        avklaringsbehov = emptyList(),
        behandlingOpprettetTidspunkt = LocalDateTime.parse("2025-12-08T08:53:12.000"),
        versjon = "UKJENT",
        mottattTid = LocalDateTime.parse("2025-12-08T08:53:12.000"),
        sakStatus = SakStatus.UTREDES,
        hendelsesTidspunkt = LocalDateTime.parse("2025-12-08T08:53:15.030000"),
        vurderingsbehov = listOf(Vurderingsbehov.MELDEKORT),
        årsakTilOpprettelse = ÅrsakTilOpprettelse.MELDEKORT,
        avsluttetBehandling = AvsluttetBehandlingDTO(
            tilkjentYtelse = TilkjentYtelseDTO(emptyList()),
            vilkårsResultat = VilkårsResultatDTO(TypeBehandling.Revurdering, emptyList()),
            beregningsGrunnlag = null,
            diagnoser = Diagnoser("SBC", "BC", emptyList()),
            rettighetstypePerioder = emptyList(),
            resultat = null,
            vedtakstidspunkt = LocalDateTime.parse("2025-12-08T08:53:14.437000"),
            perioderMedArbeidsopptrapping = emptyList(),
            vedtattStansOpphør = emptyList()
        )
    )

    @Test
    fun `resende meldekort-behandlinger`(
        @Postgres dataSource: DataSource, @Fakes azureConfig: AzureConfig
    ) {
        val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)
        val testJobber = konstruerTestJobber()

        val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb()
        val lagreAvklaringsbehovHendelseJobb =
            LagreAvklaringsbehovHendelseJobb(testJobber.motorJobbAppender)

        val motor = konstruerManuellMotor(
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
            testJobber.motorJobbAppender,
        ) {

            postBehandlingsflytHendelse(meldekorthendelse)

            motor.kjørJobber()

            oppdatertBehandlingHendelse(meldekorthendelse)

            motor.kjørJobber()

            val bqBehandlinger = dataSource.transaction {
                val behandling =
                    BehandlingRepository(it).hent(meldekorthendelse.behandlingReferanse)!!

                it.provider().provide<SakstatistikkRepository>()
                    .hentAlleHendelserPåBehandling(behandling.referanse)

            }
            bqBehandlinger.forEach {
                println(it)
            }
            assertThat(bqBehandlinger).hasSize(4)
            assertThat(bqBehandlinger.map { it.behandlingStatus }).containsSubsequence(
                "OPPRETTET",
                "AVSLUTTET"
            )
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

        val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb()
        val lagreAvklaringsbehovHendelseJobb =
            LagreAvklaringsbehovHendelseJobb(testJobber.motorJobbAppender)

        val motor = konstruerManuellMotor(
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
            testJobber.motorJobbAppender,
        ) {

            postBehandlingsflytHendelse(hendelse)

            motor.kjørJobber()
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
        assertThat(uthentetBehandling.behandlingStatus()).isEqualTo(hendelse.behandlingStatus.tilDomene())

        assertThat(avsluttetBehandlingCounter.count()).isEqualTo(0.0)
        assertThat(stoppetHendelseLagretCounter.count()).isEqualTo(1.0)

        motor.stop()
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

        val lagreOppgaveJobb = LagreOppgaveJobb()
        val lagreOppgaveHendelseJobb = LagreOppgaveHendelseJobb()
        val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb()

        val lagreSakinfoTilBigQueryJobb = LagreSakinfoTilBigQueryJobb()

        val lagreAvsluttetBehandlingTilBigQueryJobb =
            LagreAvsluttetBehandlingTilBigQueryJobb(FakeBQYtelseRepository())

        val resendSakstatistikkJobb = ResendSakstatistikkJobb()
        val jobbAppender = MotorJobbAppender()
        val lagreStoppetHendelseJobb = ekteLagreStoppetHendelseJobb(jobbAppender)
        val motor = ManuellMotorImpl(
            dataSource = dataSource,
            jobber = listOf(
                resendSakstatistikkJobb,
                lagreAvsluttetBehandlingTilBigQueryJobb,
                lagreSakinfoTilBigQueryJobb,
                LagreAvklaringsbehovHendelseJobb(jobbAppender),
                lagrePostmottakHendelseJobb,
                lagreOppgaveHendelseJobb,
                lagreOppgaveJobb,
                lagreStoppetHendelseJobb
            ),
            repositoryRegistry = postgresRepositoryRegistry,
            gatewayProvider = defaultGatewayProvider { },
        )

        testKlient(
            transactionExecutor,
            motor,
            azureConfig,
            lagreStoppetHendelseJobb,
            jobbAppender,
        ) {
            postPostmottakHendelse(hendelse)

            motor.kjørJobber()
        }

        dataSource.transaction {
            assertThat(avsluttetBehandlingCounter.count()).isEqualTo(0.0)
            assertThat(stoppetHendelseLagretCounter.count()).isEqualTo(0.0)
            assertThat(meterRegistry.lagretPostmottakHendelse().count()).isEqualTo(1.0)
        }

        motor.stop()
    }

    @Test
    fun `kan motta tilbakekrevingshendelse, og hendelse blir lagret`(
        @Postgres dataSource: DataSource, @Fakes azureConfig: AzureConfig
    ) {
        val behandlingRef = UUID.randomUUID()
        val saksnummer = "123"
        val stoppetBehandling = opprettTestStoppetBehandling(
            behandlingReferanse = behandlingRef,
            behandlingOpprettetTidspunkt = LocalDateTime.now(),
            hendelsesTidspunkt = LocalDateTime.now(),
            mottattTid = LocalDateTime.now(),
            saksnummer = saksnummer,
        )
        val tilbakekrevingshendelse = TilbakekrevingsbehandlingOppdatertHendelse(
            personIdent = "12345678901",
            saksnummer = no.nav.aap.behandlingsflyt.kontrakt.sak.Saksnummer(saksnummer),
            behandlingref = BehandlingReferanse(behandlingRef),
            behandlingStatus = TilbakekrevingBehandlingsstatus.OPPRETTET,
            sakOpprettet = LocalDateTime.of(2025, 1, 1, 10, 0, 0),
            totaltFeilutbetaltBeløp = BigDecimal("12345.67"),
            saksbehandlingURL = "https://tilbakekreving.nav.no/behandling/$behandlingRef",
        )

        val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)
        val testJobber = konstruerTestJobber()
        val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb()
        val lagreAvklaringsbehovHendelseJobb =
            LagreAvklaringsbehovHendelseJobb(testJobber.motorJobbAppender)
        val lagreStoppetHendelseJobb =
            LagreStoppetHendelseJobb(testJobber.motorJobbAppender, testJobber.lagreAvsluttetBehandlingTilBigQueryJobb)

        val motor = konstruerManuellMotor(
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
            lagreStoppetHendelseJobb,
            testJobber.motorJobbAppender,
        ) {
            // Opprett behandling først slik at tilbakekrevingshendelsen kan referere til den
            postBehandlingsflytHendelse(stoppetBehandling)
            motor.kjørJobber()

            postTilbakekrevingshendelse(tilbakekrevingshendelse)
            motor.kjørJobber()
        }

        val lagretHendelse = dataSource.transaction {
            TilbakekrevingHendelseRepositoryImpl(it).hent(behandlingRef.toString())
        }

        assertThat(lagretHendelse)
            .isNotNull()
            .usingRecursiveComparison()
            .ignoringFields("opprettetTid")
            .isEqualTo(
                TilbakekrevingHendelse(
                    saksnummer = Saksnummer(saksnummer),
                    behandlingRef = behandlingRef.toString(),
                    behandlingStatus = TilbakekrevingBehandlingStatus.OPPRETTET,
                    sakOpprettet = LocalDateTime.of(2025, 1, 1, 10, 0, 0),
                    totaltFeilutbetaltBeløp = BigDecimal("12345.67"),
                    saksbehandlingURL = "https://tilbakekreving.nav.no/behandling/$behandlingRef",
                    opprettetTid = LocalDateTime.now(),
                )
            )

        motor.stop()
    }
}
