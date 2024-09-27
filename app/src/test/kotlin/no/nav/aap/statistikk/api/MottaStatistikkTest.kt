package no.nav.aap.statistikk.api

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.Motor
import no.nav.aap.motor.mdc.NoExtraLogInfoProvider
import no.nav.aap.statistikk.api_kontrakt.*
import no.nav.aap.statistikk.avsluttetBehandlingDtoLagret
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.db.FellesKomponentTransactionalExecutor
import no.nav.aap.statistikk.hendelseLagret
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.jobber.LagreAvsluttetBehandlingDTOJobb
import no.nav.aap.statistikk.jobber.LagreAvsluttetBehandlingJobbKonstruktør
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import no.nav.aap.statistikk.testutils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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

        val jobbAppender = MockJobbAppender()

        val motor = motorMock()

        val bqRepository = FakeBQRepository()
        val meterRegistry = SimpleMeterRegistry()

        val avsluttetBehandlingCounter = meterRegistry.avsluttetBehandlingLagret()
        val stoppetHendelseLagretCounter = meterRegistry.hendelseLagret()

        testKlient(
            noOpTransactionExecutor,
            motor,
            jobbAppender,
            LagreAvsluttetBehandlingDTOJobb(
                LagreAvsluttetBehandlingJobbKonstruktør(
                    bqRepository,
                    avsluttetBehandlingCounter
                ),
                meterRegistry.avsluttetBehandlingDtoLagret()
            ),
            azureConfig,
            LagreStoppetHendelseJobb(bqRepository, stoppetHendelseLagretCounter)
        ) { url, client ->
            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"), PostRequest(
                    StoppetBehandling(
                        saksnummer = "123",
                        status = "OPPRETTET",
                        behandlingType = TypeBehandling.Førstegangsbehandling,
                        ident = "0",
                        behandlingReferanse = behandlingReferanse,
                        behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                        avklaringsbehov = listOf(),
                        versjon = "UKJENT"
                    )
                )
            )
        }

        assertThat(jobbAppender.jobber.first().payload()).isEqualTo(
            DefaultJsonMapper.toJson(
                StoppetBehandling(
                    saksnummer = "123",
                    status = "OPPRETTET",
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    ident = "0",
                    behandlingReferanse = behandlingReferanse,
                    behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                    avklaringsbehov = listOf(),
                    versjon = "UKJENT"
                )
            )
        )
    }

    @Test
    fun `kan motta mer avansert object`(
        @Postgres dataSource: DataSource, @Fakes azureConfig: AzureConfig
    ) {
        val hendelse = StoppetBehandling(
            saksnummer = "4LFK2S0",
            behandlingReferanse = UUID.fromString("96175156-0950-475a-8de0-41a25f4c0cec"),
            status = "UTREDES",
            behandlingType = TypeBehandling.Førstegangsbehandling,
            ident = "14890097570",
            avklaringsbehov = listOf(
                AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = "5003",
                        behovType = BehovType.valueOf("MANUELT_PÅKREVD"),
                        løsesISteg = "AVKLAR_SYKDOM"
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
                        løsesISteg = "VURDER_BISTANDSBEHOV"
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
                        løsesISteg = "KVALITETSSIKRING"
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
            versjon = "UKJENT"
        )

        val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)

        val bqRepository = FakeBQRepository()
        val meterRegistry = SimpleMeterRegistry()

        val stoppetHendelseLagretCounter = meterRegistry.hendelseLagret()
        val avsluttetBehandlingCounter = meterRegistry.avsluttetBehandlingLagret()
        val avsluttetBehandlingDtoLagretCounter = meterRegistry.avsluttetBehandlingDtoLagret()

        val motor = Motor(
            dataSource = dataSource,
            antallKammer = 2,
            logInfoProvider = NoExtraLogInfoProvider,
            jobber = listOf(
                LagreStoppetHendelseJobb(
                    bqRepository,
                    stoppetHendelseLagretCounter
                )
            )
        )

        val jobbAppender = MotorJobbAppender(dataSource)

        testKlient(
            transactionExecutor,
            motor,
            jobbAppender,
            LagreAvsluttetBehandlingDTOJobb(
                LagreAvsluttetBehandlingJobbKonstruktør(
                    bqRepository,
                    avsluttetBehandlingCounter
                ),
                avsluttetBehandlingDtoLagretCounter
            ),
            azureConfig,
            LagreStoppetHendelseJobb(bqRepository, stoppetHendelseLagretCounter)
        ) { url, client ->

            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"),
                PostRequest(hendelse)
            )

            dataSource.transaction(readOnly = true) {
                ventPåSvar({
                    HendelsesRepository(
                        it
                    ).hentHendelser()
                },
                    { it?.isNotEmpty() ?: false })
            }

            dataSource.transaction {
                val hendelsesRepository = HendelsesRepository(
                    it
                )
                val hentHendelser = hendelsesRepository.hentHendelser()

                assertThat(hentHendelser).hasSize(1)
                assertThat(hentHendelser.first().behandlingReferanse).isEqualTo(hendelse.behandlingReferanse)
                assertThat(hentHendelser.first().saksnummer).isEqualTo(hendelse.saksnummer)
                assertThat(hentHendelser.first().behandlingOpprettetTidspunkt).isEqualTo(
                    hendelse.behandlingOpprettetTidspunkt
                )
                assertThat(hentHendelser.first().behandlingType).isEqualTo(hendelse.behandlingType)
                assertThat(hentHendelser.first().status).isEqualTo(hendelse.status)
            }
        }
    }
}