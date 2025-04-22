package no.nav.aap.statistikk

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.FATTE_VEDTAK
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.testutil.TestUtil
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.statistikk.api.stringToNumber
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.hendelser.utledGjeldendeAvklaringsBehov
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobb
import no.nav.aap.statistikk.oppgave.OppgaveHendelse
import no.nav.aap.statistikk.pdl.PdlConfig
import no.nav.aap.statistikk.sak.SakTabell
import no.nav.aap.statistikk.sak.tilSaksnummer
import no.nav.aap.statistikk.testutils.*
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelseTabell
import no.nav.aap.statistikk.vilkårsresultat.VilkårsVurderingTabell
import no.nav.aap.statistikk.vilkårsresultat.Vilkårtype
import org.assertj.core.api.Assertions.assertThat
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

    data class Jobbdump(
        val payload: String,
        val type: String,
        @JsonDeserialize(using = LocalDateTimeDeserializer::class)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        @JsonProperty("opprettet_tid") val opprettetTid: LocalDateTime
    )

    data class Dump(val jobb: List<Jobbdump>)

    sealed interface HendelseData {
        val opprettetTidspunkt: LocalDateTime
        val data: Any
    }

    data class BehandlingHendelseData(
        override val data: StoppetBehandling,
        override val opprettetTidspunkt: LocalDateTime
    ) : HendelseData

    data class OppgaveHendelseData(
        override val data: DomeneOppgaveHendelse,
        override val opprettetTidspunkt: LocalDateTime
    ) : HendelseData

    private val logger = LoggerFactory.getLogger(IntegrationTest::class.java)

    @Test
    fun `dump av ekte hendelser`(
        @Postgres dbConfig: DbConfig,
        @Postgres dataSource: DataSource,
        @BigQuery config: BigQueryConfig,
        @Fakes azureConfig: AzureConfig,
        @Fakes pdlConfig: PdlConfig,
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
                    TODO()
                }
            }
        }

        val testUtil = TestUtil(dataSource, listOf("oppgave.retryFeilede"))

        val bigQueryClient = bigQueryClient(config)
        var referanse: UUID? = null
        testKlientNoInjection(
            dbConfig,
            pdlConfig = pdlConfig,
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
                { it != null }
            )

            assertThat(behandling!!.status).isEqualTo(BehandlingStatus.AVSLUTTET)
        }

        // Sekvensnummer økes med 1 med ny info på sak
        val bqSaker2 = ventPåSvar(
            { bigQueryClient.read(SakTabell()) },
            { t -> t !== null && t.isNotEmpty() && t.size > 2 })
        assertThat(bqSaker2!!).hasSize(hendelserFraDBDump.size)
        assertThat(bqSaker2.map { it.ansvarligEnhetKode }).containsAnyOf("4491", "5701", "5700")
        assertThat(bqSaker2.map { it.behandlingStatus }).containsSubsequence(
            "UNDER_BEHANDLING",
            "UNDER_BEHANDLING_SENDT_TILBAKE_FRA_KVALITETSSIKRER",
            "UNDER_BEHANDLING_SENDT_TILBAKE_FRA_BESLUTTER",
            "IVERKSETTES",
            "AVSLUTTET"
        )
        assertThat(bqSaker2.map { it.behandlingStatus }.toSet()).containsExactlyInAnyOrder(
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
            { bigQueryClient.read(TilkjentYtelseTabell()) },
            { t -> t !== null && t.isNotEmpty() })

        assertThat(bqYtelse!!).allSatisfy {
            assertThat(it.behandlingsreferanse).isEqualTo(referanse.toString())
            assertThat(it.dagsats).isEqualTo(974.0)
            assertThat(it.antallBarn).isEqualTo(1)
        }
    }

    private fun postOppgaveHendelse(
        dataSource: DataSource,
        hendelse: OppgaveHendelse
    ) {
        dataSource.transaction {
            FlytJobbRepository(it).leggTil(
                JobbInput(LagreOppgaveHendelseJobb(SimpleMeterRegistry(), object : JobbAppender {
                    override fun leggTil(connection: DBConnection, jobb: JobbInput) {
                        TODO()
                    }

                    override fun leggTilLagreSakTilBigQueryJobb(
                        connection: DBConnection,
                        behandlingId: BehandlingId
                    ) {
                        println("!!!!!!!!!!!!!!!!!!!!!!")
                        TODO()
                    }

                    override fun leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
                        connection: DBConnection,
                        behandlingId: BehandlingId
                    ) {
                        TODO("Not yet implemented")
                    }
                })).medPayload(
                    DefaultJsonMapper.toJson(hendelse)
                ).forSak(stringToNumber(hendelse.saksnummer!!))
            )
        }

    }

    private fun postBehandlingsflytHendelse(
        url: String,
        client: RestClient<InputStream>,
        hendelse: StoppetBehandling
    ) {
        client.post<StoppetBehandling, Any>(
            URI.create("$url/stoppetBehandling"),
            PostRequest(hendelse)
        )
    }

    @Test
    fun `test flyt`(
        @Postgres dbConfig: DbConfig,
        @Postgres dataSource: DataSource,
        @BigQuery config: BigQueryConfig,
        @Fakes azureConfig: AzureConfig,
        @Fakes pdlConfig: PdlConfig,
    ) {

        val behandlingReferanse = UUID.randomUUID()
        val saksnummer = "4LFK2S0".tilSaksnummer()
        val hendelseFørCopy = behandlingHendelse(saksnummer, behandlingReferanse)
        val avsluttetBehandling = avsluttetBehandlingDTO()

        val hendelse = gjørHendelseAvsluttet(hendelseFørCopy)

        val bigQueryClient = bigQueryClient(config)

        val testUtil = TestUtil(dataSource, listOf("oppgave.retryFeilede"))
        testKlientNoInjection(
            dbConfig,
            pdlConfig = pdlConfig,
            azureConfig = azureConfig,
            bigQueryClient,
        ) { url, client ->
            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"),
                PostRequest(hendelse)
            )

            client.post<no.nav.aap.oppgave.statistikk.OppgaveHendelse, Any>(
                URI.create("$url/oppgave"), PostRequest(
                    no.nav.aap.oppgave.statistikk.OppgaveHendelse(
                        hendelse = HendelseType.OPPRETTET,
                        oppgaveDto = OppgaveDto(
                            id = 1,
                            personIdent = hendelse.ident,
                            saksnummer = hendelse.saksnummer,
                            behandlingRef = hendelse.behandlingReferanse,
                            enhet = "0400",
                            oppfølgingsenhet = null,
                            behandlingOpprettet = hendelse.behandlingOpprettetTidspunkt,
                            avklaringsbehovKode = hendelse.avklaringsbehov.utledGjeldendeAvklaringsBehov()!!,
                            status = no.nav.aap.oppgave.verdityper.Status.OPPRETTET,
                            behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
                            reservertAv = "Karl Korrodheid",
                            reservertTidspunkt = LocalDateTime.now(),
                            opprettetAv = "Kelvin",
                            opprettetTidspunkt = LocalDateTime.now(),
                        )
                    )
                )
            )

            testUtil.ventPåSvar()
            val behandling = ventPåSvar(
                { dataSource.transaction { BehandlingRepository(it).hent(behandlingReferanse) } },
                { it != null }
            )
            assertThat(behandling).isNotNull
            assertThat(behandling!!.behandlendeEnhet!!.kode).isEqualTo("0400")

            testUtil.ventPåSvar()
            val bqSaker = ventPåSvar(
                { bigQueryClient.read(SakTabell()).sortedBy { it.sekvensNummer } },
                { t -> t !== null && t.isNotEmpty() && t.size == 2 })
            assertThat(bqSaker).isNotNull
            assertThat(bqSaker).hasSize(2)
            assertThat(bqSaker!!.first().sekvensNummer).isEqualTo(1)

            assertThat(bqSaker[1].ansvarligEnhetKode).isEqualTo("0400")

            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"),
                PostRequest(
                    hendelse.copy(
                        behandlingStatus = Status.AVSLUTTET,
                        avsluttetBehandling = avsluttetBehandling
                    )
                )
            )

            // Sekvensnummer økes med 1 med ny info på sak
            val bqSaker2 = ventPåSvar(
                { bigQueryClient.read(SakTabell()) },
                { t -> t !== null && t.isNotEmpty() && t.size > 2 })
            assertThat(bqSaker2!!).hasSize(3)
            assertThat(bqSaker2[2].sekvensNummer).isEqualTo(3)

            val bigQueryRespons =
                ventPåSvar(
                    { bigQueryClient.read(VilkårsVurderingTabell()) },
                    { t -> t !== null && t.isNotEmpty() })

            assertThat(bigQueryRespons).hasSize(1)
            val vilkårsVurderingRad = bigQueryRespons!!.first()

            assertThat(vilkårsVurderingRad.behandlingsReferanse).isEqualTo(behandlingReferanse)
            assertThat(vilkårsVurderingRad.saksnummer).isEqualTo(saksnummer)
            assertThat(vilkårsVurderingRad.vilkårtype).isEqualTo(Vilkårtype.ALDERSVILKÅRET)

            val tilkjentBigQuery = ventPåSvar(
                { bigQueryClient.read(TilkjentYtelseTabell()) },
                { t -> t !== null && t.isNotEmpty() })

            assertThat(tilkjentBigQuery!!).hasSize(2)
            val tilkjentYtelse = tilkjentBigQuery.first()
            assertThat(tilkjentYtelse.behandlingsreferanse).isEqualTo(behandlingReferanse.toString())
            assertThat(tilkjentYtelse.dagsats).isEqualTo(avsluttetBehandling.tilkjentYtelse.perioder[0].dagsats)
            assertThat(tilkjentBigQuery[1].dagsats).isEqualTo(avsluttetBehandling.tilkjentYtelse.perioder[1].dagsats)


            val sakRespons = ventPåSvar(
                { bigQueryClient.read(SakTabell()) },
                { t -> t !== null && t.isNotEmpty() })

            assertThat(sakRespons).hasSize(3)
            assertThat(sakRespons!!.first().saksbehandler).isEqualTo("Z994573")
            assertThat(sakRespons.first().vedtakTid).isEqualTo(
                LocalDateTime.of(
                    2024,
                    10,
                    18,
                    11,
                    7,
                    27
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


    private fun gjørHendelseAvsluttet(hendelseFørCopy: StoppetBehandling) =
        hendelseFørCopy.copy(
            avklaringsbehov = hendelseFørCopy.avklaringsbehov + listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = FATTE_VEDTAK,
                    status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                    endringer = listOf(
                        EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                            tidsstempel = LocalDateTime.parse("2024-10-18T11:07:17.882"),
                            endretAv = "Kelvin"
                        ),
                        EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                            tidsstempel = LocalDateTime.parse("2024-10-18T11:07:27.634"),
                            endretAv = "Z994573"
                        )
                    )
                ),
            )
        )
}