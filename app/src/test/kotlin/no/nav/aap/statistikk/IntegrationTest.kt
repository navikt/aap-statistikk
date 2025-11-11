package no.nav.aap.statistikk

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.testutil.TestUtil
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.statistikk.OppgaveTilStatistikkDto
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.statistikk.api.stringToNumber
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.BehandlingTabell
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.hendelser.påTidspunkt
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobb
import no.nav.aap.statistikk.oppgave.LagreOppgaveJobb
import no.nav.aap.statistikk.oppgave.OppgaveHendelse
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepositoryImpl
import no.nav.aap.statistikk.saksstatistikk.SakstatistikkRepositoryImpl
import no.nav.aap.statistikk.testutils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource
import no.nav.aap.statistikk.oppgave.OppgaveHendelse as DomeneOppgaveHendelse

@Fakes
class IntegrationTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            val azp = UUID.randomUUID().toString()
            System.setProperty("integrasjon.postmottak.azp", azp)
            System.setProperty("integrasjon.behandlingsflyt.azp", azp)
            System.setProperty("integrasjon.oppgave.azp", azp)
        }
    }

    data class Jobbdump(
        val payload: String,
        val type: String,
        @param:JsonDeserialize(using = LocalDateTimeDeserializer::class) @param:JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd HH:mm:ss.SSS"
        ) @param:JsonProperty("opprettet_tid") val opprettetTid: LocalDateTime
    )

    data class Dump(val jobb: List<Jobbdump>)

    sealed interface HendelseData {
        val opprettetTidspunkt: LocalDateTime
        val data: Any
    }

    data class BehandlingHendelseData(
        override val data: StoppetBehandling, override val opprettetTidspunkt: LocalDateTime
    ) : HendelseData

    data class OppgaveHendelseData(
        override val data: DomeneOppgaveHendelse, override val opprettetTidspunkt: LocalDateTime
    ) : HendelseData

    private val logger = LoggerFactory.getLogger(IntegrationTest::class.java)

    @Test
    fun `dump av ekte hendelser`(
        @Postgres dbConfig: DbConfig,
        @Postgres dataSource: DataSource,
        @BigQuery config: BigQueryConfig,
        @Fakes azureConfig: AzureConfig,
    ) {
        // hent på nytt slik: select payload, opprettet_tid, type from jobb where sak_id = 783332 order by opprettet_tid
        // og lagre output som json
        val lines = object {}.javaClass.getResource("/hendelser_public_jobb.json")?.readText()
        assertThat(lines).isNotNull

        val value = """
            {"jobb": $lines}
        """.trimIndent()
        val hendelserFraDBDump = DefaultJsonMapper.fromJson<Dump>(value).jobb.map {
            when (it.type) {
                "statistikk.lagreOppgaveHendelseJobb" -> {
                    OppgaveHendelseData(DefaultJsonMapper.fromJson(it.payload), it.opprettetTid)
                }

                "statistikk.lagreHendelse" -> {
                    BehandlingHendelseData(DefaultJsonMapper.fromJson(it.payload), it.opprettetTid)
                }

                else -> {
                    error("Uhåndtert $it")
                }
            }
        }

        val avsluttetBehandlingHendelser =
            hendelserFraDBDump.filterIsInstance<BehandlingHendelseData>()
                .filter { it.data.avsluttetBehandling != null }
        assertThat(
            avsluttetBehandlingHendelser
        ).hasSize(1)

        val testUtil = TestUtil(dataSource, listOf("oppgave.retryFeilede"))

        val bigQueryClient = bigQueryClient(config)
        var referanse: UUID? = null
        testKlientNoInjection(
            dbConfig,
            azureConfig = azureConfig,
            bigQueryClient,
        ) { url, client ->

            var c = 1
            hendelserFraDBDump.forEach {
                when (it) {
                    is BehandlingHendelseData -> {
                        postBehandlingsflytHendelse(url, client, it.data)
                        referanse = it.data.behandlingReferanse
                        testUtil.ventPåSvar()
                    }

                    is OppgaveHendelseData -> {
                        postOppgaveHendelse(dataSource, it.data)
                        testUtil.ventPåSvar()
                    }
                }
                logger.info("Hendelse nr ${c++}: ${it.data::class.simpleName} av ${hendelserFraDBDump.size}")
            }
            testUtil.ventPåSvar()

            val behandling = ventPåSvar(
                { dataSource.transaction { BehandlingRepository(it).hent(referanse!!) } },
                { it != null })

            assertThat(behandling!!.status).isEqualTo(BehandlingStatus.AVSLUTTET)
        }

        // Sekvensnummer økes med 1 med ny info på sak
        val bqSaker2 = ventPåSvar(
            { dataSource.transaction { SakstatistikkRepositoryImpl(it).hentAlleHendelserPåBehandling(referanse!!) } },
            { t -> t !== null && t.isNotEmpty() && t.size > 2 })
//        assertThat(bqSaker2!!).hasSize(hendelserFraDBDump.size)
        assertThat(bqSaker2!!.map { it.ansvarligEnhetKode }).contains("4491", "5701", "5700")
        assertThat(bqSaker2.map { it.behandlingStatus }).containsSubsequence(
            "UNDER_BEHANDLING",
            "UNDER_BEHANDLING_SENDT_TILBAKE_FRA_KVALITETSSIKRER",
            "UNDER_BEHANDLING_SENDT_TILBAKE_FRA_BESLUTTER",
            "IVERKSETTES",
            "AVSLUTTET"
        )
        assertThat(bqSaker2.map { it.behandlingStatus }.toSet()).containsExactlyInAnyOrder(
            "OPPRETTET",
            "UNDER_BEHANDLING",
            "IVERKSETTES",
            "AVSLUTTET",
            "UNDER_BEHANDLING_SENDT_TILBAKE_FRA_BESLUTTER",
            "UNDER_BEHANDLING_SENDT_TILBAKE_FRA_KVALITETSSIKRER"
        )
        assertThat(bqSaker2.filter { it.resultatBegrunnelse != null }).isNotEmpty
        assertThat(bqSaker2.filter { it.resultatBegrunnelse != null }).allSatisfy {
            assertThat(it.behandlingStatus).contains("SENDT_TILBAKE")
        }

        assertThat(bqSaker2.map { it.behandlingMetode.name }).containsSubsequence(
            "MANUELL",
            "KVALITETSSIKRING",
            "MANUELL",
            "KVALITETSSIKRING",
            "MANUELL",
            "KVALITETSSIKRING",
            "MANUELL",
            "KVALITETSSIKRING",
            "MANUELL",
            "FATTE_VEDTAK",
            "MANUELL",
            "FATTE_VEDTAK",
            "MANUELL",
            "FATTE_VEDTAK",
            "MANUELL",
            "FATTE_VEDTAK",
            "MANUELL",
        )

        // Sjekk ytelsesstatistikk
        val bqYtelse = ventPåSvar(
            { bigQueryClient.read(BehandlingTabell()) },
            { t -> t !== null && t.isNotEmpty() })

        assertThat(bqYtelse!!).allSatisfy {
            assertThat(it.referanse).isEqualTo(referanse)
            assertThat(it.datoAvsluttet).isEqualTo(
                LocalDateTime.of(2025, 4, 15, 13, 28, 11, 127000000)
            )
            assertThat(it.resultat).isEqualTo(ResultatKode.valueOf(("INNVILGET")))
        }

        dataSource.transaction {
            SakstatistikkRepositoryImpl(it).hentAlleHendelserPåBehandling(
                referanse!!
            )
        }.let {
            val avsluttede = it.filter { it.behandlingStatus == "AVSLUTTET" }
            println("...")
            println(avsluttede)
            println("...")
        }


        val alleHendelser = dataSource.transaction {
            SakstatistikkRepositoryImpl(it).hentAlleHendelserPåBehandling(referanse!!)
        }

        // Forvent kun én inngangshendelse.
        assertThat(alleHendelser.filter { it.endretTid == it.registrertTid }.size).isEqualTo(1)


        // DEL 2: test resending
        testKlientNoInjection(
            dbConfig,
            azureConfig = azureConfig,
            bigQueryClient,
        ) { url, client ->

            client.post<StoppetBehandling, Any>(
                URI.create("$url/oppdatertBehandling"),
                PostRequest(avsluttetBehandlingHendelser.last().data)
            )

            testUtil.ventPåSvar()
        }

        val alleSakstatistikkHendelser = dataSource.transaction {
            SakstatistikkRepositoryImpl(it).hentAlleHendelserPåBehandling(
                avsluttetBehandlingHendelser.last().data.behandlingReferanse
            ).sortedBy { it.endretTid }
        }

        val resendinger =
            alleSakstatistikkHendelser.filter { it.erResending }.sortedBy { it.endretTid }

        // Gjør samme sjekker som for vanlige sending
        // TODO: lage testdataen på nytt, slik at dette kan testes
        assertThat(resendinger.map { it.ansvarligEnhetKode }).contains("4491", "5701", "5700")
        assertThat(resendinger).extracting("behandlingStatus").containsSubsequence(
            "UNDER_BEHANDLING",
            "UNDER_BEHANDLING_SENDT_TILBAKE_FRA_KVALITETSSIKRER",
            "UNDER_BEHANDLING_SENDT_TILBAKE_FRA_BESLUTTER",
            "IVERKSETTES",
            "AVSLUTTET"
        )
        assertThat(resendinger.map { it.behandlingStatus }.toSet()).containsExactlyInAnyOrder(
            "UNDER_BEHANDLING",
            "IVERKSETTES",
            "AVSLUTTET",
            "UNDER_BEHANDLING_SENDT_TILBAKE_FRA_BESLUTTER",
            "UNDER_BEHANDLING_SENDT_TILBAKE_FRA_KVALITETSSIKRER"
        )
        assertThat(resendinger.filter { it.resultatBegrunnelse != null }).isNotEmpty
        assertThat(resendinger.filter { it.resultatBegrunnelse != null }).allSatisfy {
            assertThat(it.behandlingStatus).contains("SENDT_TILBAKE")
        }

        assertThat(resendinger.map { it.behandlingMetode.name }).containsSubsequence(
            "MANUELL",
            "KVALITETSSIKRING",
            "MANUELL",
            "KVALITETSSIKRING",
            "MANUELL",
            "KVALITETSSIKRING",
            "MANUELL",
            "KVALITETSSIKRING",
            "MANUELL",
            "FATTE_VEDTAK",
            "MANUELL",
        )

        println("Alle sakstatistikk hendelser: ${alleSakstatistikkHendelser.size}")
        println("Resendinger: ${resendinger.size}")
    }

    private fun postOppgaveHendelse(
        dataSource: DataSource, hendelse: OppgaveHendelse
    ) {
        dataSource.transaction {
            FlytJobbRepository(it).leggTil(
                JobbInput(
                    LagreOppgaveHendelseJobb(
                        LagreOppgaveJobb()
                    )
                ).medPayload(
                    DefaultJsonMapper.toJson(hendelse)
                ).forSak(stringToNumber(hendelse.saksnummer!!))
            )
        }

    }

    private fun postBehandlingsflytHendelse(
        url: String, client: RestClient<InputStream>, hendelse: StoppetBehandling
    ) {
        client.post<StoppetBehandling, Any>(
            URI.create("$url/stoppetBehandling"), PostRequest(hendelse)
        )
    }

    @Test
    fun `test flyt`(
        @Postgres dbConfig: DbConfig,
        @Postgres dataSource: DataSource,
        @BigQuery config: BigQueryConfig,
        @Fakes azureConfig: AzureConfig,
    ) {

        val behandlingReferanse = UUID.fromString("ca0a378d-9249-47b3-808a-afe6a6357ac5")

        val hendelse =
            object {}.javaClass.getResource("/avklaringsbehovhendelser/fullfort_forstegangsbehandling.json")!!
                .readText().let { DefaultJsonMapper.fromJson<StoppetBehandling>(it) }

        val tidspunkter =
            hendelse.avklaringsbehov.flatMap { it.endringer.map { endringDTO -> endringDTO.tidsstempel } }
        val midtI = tidspunkter[tidspunkter.size / 2 + 1]
        val hendelsx = hendelse.copy(
            behandlingStatus = Status.UTREDES,
            avsluttetBehandling = null,
            avklaringsbehov = hendelse.avklaringsbehov.påTidspunkt(midtI),
            hendelsesTidspunkt = hendelse.hendelsesTidspunkt.minusMinutes(5)
        )

        val bigQueryClient = bigQueryClient(config)

        val testUtil = TestUtil(dataSource, listOf("oppgave.retryFeilede"))
        testKlientNoInjection(
            dbConfig,
            azureConfig = azureConfig,
            bigQueryClient,
        ) { url, client ->

            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"), PostRequest(hendelsx)
            )

            testUtil.ventPåSvar()

            val gjeldendeAVklaringsbehov =
                dataSource.transaction { BehandlingRepository(it).hent(hendelse.behandlingReferanse)!!.gjeldendeAvklaringsBehov }!!
                    .let { Definisjon.forKode(it) }

            client.post<no.nav.aap.oppgave.statistikk.OppgaveHendelse, Any>(
                URI.create("$url/oppgave"), PostRequest(
                    no.nav.aap.oppgave.statistikk.OppgaveHendelse(
                        hendelse = HendelseType.OPPRETTET,
                        oppgaveTilStatistikkDto = OppgaveTilStatistikkDto(
                            id = 1,
                            personIdent = hendelse.ident,
                            saksnummer = hendelse.saksnummer,
                            behandlingRef = hendelse.behandlingReferanse,
                            enhet = "0400",
                            avklaringsbehovKode = gjeldendeAVklaringsbehov.kode.name,
                            status = no.nav.aap.oppgave.verdityper.Status.OPPRETTET,
                            behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
                            reservertAv = "Karl Korrodheid",
                            reservertTidspunkt = midtI,
                            opprettetAv = "Kelvin",
                            opprettetTidspunkt = midtI,
                        )
                    )
                )
            )
            testUtil.ventPåSvar()


            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"), PostRequest(hendelse)
            )

            testUtil.ventPåSvar()
            val behandling = ventPåSvar(
                {
                    dataSource.transaction {
                        BehandlingRepository(it).hent(behandlingReferanse)
                    }
                },
                { it != null })
            assertThat(behandling).isNotNull
            val enhet = dataSource.transaction {
                OppgaveHendelseRepositoryImpl(it).hentEnhetForAvklaringsbehov(
                    behandling!!.referanse,
                    gjeldendeAVklaringsbehov.kode.name,
                ).last()
            }
            assertThat(enhet.enhet).isEqualTo("0400")

            testUtil.ventPåSvar()

//            assertThat(bqSaker).anySatisfy {
//                assertThat(it.ansvarligEnhetKode).isEqualTo("0400")
//            }

            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"), PostRequest(
                    hendelse.copy(
                        behandlingStatus = Status.AVSLUTTET,
                        avsluttetBehandling = hendelse.avsluttetBehandling
                    )
                )
            )
        }
    }

    private fun bigQueryClient(config: BigQueryConfig): BigQueryClient {
        val client = BigQueryClient(config, mapOf())

        // Hack fordi emulator ikke støtter migrering
        schemaRegistry.forEach { (_, schema) ->
            client.create(schema)
        }

        return client
    }


}