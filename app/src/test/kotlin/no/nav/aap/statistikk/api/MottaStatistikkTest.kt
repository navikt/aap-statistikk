package no.nav.aap.statistikk.api

import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.checkUnnecessaryStub
import io.mockk.mockk
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.Motor
import no.nav.aap.motor.mdc.NoExtraLogInfoProvider
import no.nav.aap.statistikk.Fakes
import no.nav.aap.statistikk.FellesKomponentTransactionalExecutor
import no.nav.aap.statistikk.LagreHendelseJobb
import no.nav.aap.statistikk.MockJobbAppender
import no.nav.aap.statistikk.MotorJobbAppender
import no.nav.aap.statistikk.Postgres
import no.nav.aap.statistikk.TestToken
import no.nav.aap.statistikk.api_kontrakt.*
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.motorMock
import no.nav.aap.statistikk.noOpTransactionExecutor
import no.nav.aap.statistikk.server.authenticate.AzureConfig
import no.nav.aap.statistikk.testKlient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

@Fakes
class MottaStatistikkTest {
    @Test
    fun `hendelse blir lagret i repository`(
        @Fakes azureConfig: AzureConfig,
        @Fakes token: TestToken
    ) {
        val avsluttetBehandlingService = mockk<AvsluttetBehandlingService>()

        val behandlingReferanse = UUID.randomUUID()
        val behandlingOpprettetTidspunkt = LocalDateTime.now()

        val jobbAppender = MockJobbAppender()

        val motor = motorMock()

        testKlient(
            noOpTransactionExecutor,
            motor,
            jobbAppender,
            avsluttetBehandlingService,
            azureConfig
        ) { client ->
            val res = client.post("/motta") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${token.access_token}")
                }
                setBody(
                    MottaStatistikkDTO(
                        saksnummer = "123",
                        status = "OPPRETTET",
                        behandlingType = TypeBehandling.Førstegangsbehandling,
                        ident = "0",
                        behandlingReferanse = behandlingReferanse,
                        behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                        avklaringsbehov = listOf()
                    )
                )
            }
            Assertions.assertEquals(HttpStatusCode.Accepted, res.status)
        }

        assertThat(jobbAppender.jobber.first().payload()).isEqualTo(
            DefaultJsonMapper.toJson(
                MottaStatistikkDTO(
                    saksnummer = "123",
                    status = "OPPRETTET",
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    ident = "0",
                    behandlingReferanse = behandlingReferanse,
                    behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
                    avklaringsbehov = listOf()
                )
            )
        )

        checkUnnecessaryStub(
            avsluttetBehandlingService,
        )
    }

    @Test
    fun `kan motta mer avansert object`(
        @Postgres dataSource: DataSource,
        @Fakes azureConfig: AzureConfig,
        @Fakes token: TestToken
    ) {
        val hendelse = MottaStatistikkDTO(
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
                        ),
                        Endring(
                            status = EndringStatus.valueOf("AVSLUTTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:50:50.217"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                ),
                AvklaringsbehovHendelse(
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
                        ),
                        Endring(
                            status = EndringStatus.valueOf("AVSLUTTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:51:16.176"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                ),
                AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = "5097",
                        behovType = BehovType.valueOf("MANUELT_PÅKREVD"),
                        løsesISteg = "KVALITETSSIKRING"
                    ),
                    status = EndringStatus.valueOf("AVSLUTTET"),
                    endringer = listOf(
                        Endring(
                            status = EndringStatus.valueOf("OPPRETTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:51:17.231"),
                            frist = null,
                            endretAv = "Kelvin"
                        ),
                        Endring(
                            status = EndringStatus.valueOf("AVSLUTTET"),
                            tidsstempel = LocalDateTime.parse("2024-08-14T11:54:22.268"),
                            frist = null,
                            endretAv = "Z994573"
                        )
                    )
                )
            ),
            behandlingOpprettetTidspunkt = LocalDateTime.parse("2024-08-14T10:35:33.595")
        )

        val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)

        val motor = Motor(
            dataSource = dataSource,
            antallKammer = 2,
            logInfoProvider = NoExtraLogInfoProvider,
            jobber = listOf(LagreHendelseJobb)
        )


        val jobbAppender = MotorJobbAppender(dataSource)

        dataSource.transaction {
            val hendelsesRepository = HendelsesRepository(it)
            val avsluttetBehandlingService = mockk<AvsluttetBehandlingService>()

            testKlient(
                transactionExecutor,
                motor,
                jobbAppender,
                avsluttetBehandlingService,
                azureConfig
            ) { client ->
                val res = client.post("/motta") {
                    contentType(ContentType.Application.Json)
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${token.access_token}")
                    }
                    setBody(hendelse)
                }
                Assertions.assertEquals(HttpStatusCode.Accepted, res.status)

                ventPåSvar(dataSource)

                val hentHendelser = hendelsesRepository.hentHendelser()

                assertThat(hentHendelser).hasSize(1)
                assertThat(hentHendelser.first().behandlingReferanse).isEqualTo(hendelse.behandlingReferanse)
                assertThat(hentHendelser.first().saksnummer).isEqualTo(hendelse.saksnummer)
                assertThat(hentHendelser.first().behandlingOpprettetTidspunkt).isEqualTo(hendelse.behandlingOpprettetTidspunkt)
                assertThat(hentHendelser.first().behandlingType).isEqualTo(hendelse.behandlingType)
                assertThat(hentHendelser.first().status).isEqualTo(hendelse.status)


            }
        }
    }

    private fun ventPåSvar(dataSource: DataSource) {
        val timeInMillis = measureTimeMillis {
            dataSource.transaction(readOnly = true) {
                val maxTid = LocalDateTime.now().plusMinutes(1)
                val hendelsesRepository = HendelsesRepository(it)
                while (hendelsesRepository.hentHendelser()
                        .isEmpty() && maxTid.isAfter(LocalDateTime.now())
                ) {
                    Thread.sleep(50L)
                }
            }
        }
        println("Ventet på at prosessering skulle fullføre, det tok $timeInMillis millisekunder.")
    }
}