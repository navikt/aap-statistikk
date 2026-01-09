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
import no.nav.aap.motor.retry.DriftJobbRepositoryExposed
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
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.Vilkårtype
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
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

    private val log = LoggerFactory.getLogger(IntegrationTest::class.java)

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

        val (testUtil, bigQueryClient) = setupTestEnvironment(dataSource, config)
        lateinit var referanse: UUID
        testKlientNoInjection(
            dbConfig,
            azureConfig = azureConfig,
            bigQueryClient,
        ) { url, client ->
            referanse = prosesserHendelserOgVerifiserBehandling(
                dataSource, url, client, hendelserFraDBDump, testUtil
            )
        }

        // Sekvensnummer økes med 1 med ny info på sak
        val bqSaker2 = hentSakstatistikkHendelser(dataSource, referanse, minSize = 2)
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
        assertThat(bqSaker2.filter { it.behandlingStatus == "AVSLUTTET" }
            .onlyOrNull()?.vedtakTid).isNotNull
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

        // Sjekk tilkjent ytelse
        val tilkjentYtelse = ventPåSvar(
            { dataSource.transaction { TilkjentYtelseRepository(it).hentForBehandling(referanse)?.perioder } },
            { t -> t !== null && t.isNotEmpty() })

        assertThat(tilkjentYtelse!!).allSatisfy {
            assertThat(it.dagsats).isEqualTo(974.0)
            assertThat(it.antallBarn).isEqualTo(1)
        }

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
                referanse
            )
        }.let {
            val avsluttede = it.filter { it.behandlingStatus == "AVSLUTTET" }
            println("...")
            println(avsluttede)
            println("...")
        }

        val alleHendelser = dataSource.transaction {
            SakstatistikkRepositoryImpl(it).hentAlleHendelserPåBehandling(referanse)
        }

        // Forvent kun én inngangshendelse.
        assertThat(alleHendelser.filter { it.endretTid == it.registrertTid }.size).isEqualTo(1)

        println("RESENDING")
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
            alleSakstatistikkHendelser.filter { it.erResending }.sorted()

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
    fun `dump av hendelser for klage`(
        @Postgres dbConfig: DbConfig,
        @Postgres dataSource: DataSource,
        @BigQuery config: BigQueryConfig,
        @Fakes azureConfig: AzureConfig
    ) {
        /**
         * Filen er lagd slik:
         *  - Inne i FakeServers i behandlingsflyt, legg til denne på endepunktet for statistikk:
         *                 val jsonFile = File("klage_hendelser.json")
         *                 if (!jsonFile.exists()) {
         *                     // Create if not exists
         *                     jsonFile.createNewFile()
         *                 }
         *                 val jsonContent = DefaultJsonMapper.toJson(receive)
         *                 jsonFile.appendText(jsonContent)
         */
        val lines = object {}.javaClass.getResource("/hendelser_klage.json")?.readText()
        assertThat(lines).isNotNull

        val hendelserFraFlyt = DefaultJsonMapper.fromJson<List<StoppetBehandling>>(lines!!)

        val (testUtil, bigQueryClient) = setupTestEnvironment(dataSource, config)
        lateinit var referanse: UUID
        testKlientNoInjection(
            dbConfig,
            azureConfig = azureConfig,
            bigQueryClient,
        ) { url, client ->
            val behandlingHendelser = hendelserFraFlyt.map {
                BehandlingHendelseData(it, LocalDateTime.now())
            }
            referanse = prosesserHendelserOgVerifiserBehandling(
                dataSource, url, client, behandlingHendelser, testUtil
            )
        }

        // Sekvensnummer økes med 1 med ny info på sak
        val bqSaker2 = hentSakstatistikkHendelser(dataSource, referanse, minSize = 2)

        assertThat(bqSaker2!!.map { it.behandlingStatus }).containsSubsequence(
            "OPPRETTET",
            "UNDER_BEHANDLING",
            "IVERKSETTES",
            "OVERSENDT_KA"
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

            postBehandlingsflytHendelse(url, client, hendelsx)

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


            postBehandlingsflytHendelse(url, client, hendelse)

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
            val bqSaker = hentSakstatistikkHendelserMedEksaktAntall(
                dataSource, behandling!!.referanse, expectedSize = 3
            )
            assertThat(bqSaker).isNotNull
            assertThat(bqSaker).hasSize(3)
            assertThat(bqSaker!!.first().sekvensNummer).isEqualTo(1)

            postBehandlingsflytHendelse(
                url, client, hendelse.copy(
                    behandlingStatus = Status.AVSLUTTET,
                    avsluttetBehandling = hendelse.avsluttetBehandling
                )
            )

            testUtil.ventPåSvar()

            // Sekvensnummer økes med 1 med ny info på sak
            val bqSaker2 = hentSakstatistikkHendelser(dataSource, behandling.referanse, minSize = 2)
            assertThat(bqSaker2!!).hasSize(3)
            assertThat(bqSaker2[1].sekvensNummer).isEqualTo(2)

            val vilkårRespons = ventPåSvar(
                {
                    dataSource.transaction {
                        VilkårsresultatRepository(it).hentForBehandling(
                            behandlingReferanse
                        )
                    }.vilkår
                },
                { t -> t !== null && t.isNotEmpty() })

            assertThat(vilkårRespons).hasSize(9)
            val vilkårsVurderingRad = vilkårRespons!!.first()

            assertThat(vilkårsVurderingRad.vilkårType).isEqualTo(Vilkårtype.ALDERSVILKÅRET.name)

            val tilkjent = ventPåSvar(
                {
                    dataSource.transaction {
                        TilkjentYtelseRepository(it).hentForBehandling(
                            behandlingReferanse
                        )!!.perioder
                    }
                },
                { t -> t !== null && t.isNotEmpty() })

            assertThat(tilkjent!!).hasSize(1)
            val tilkjentYtelse = tilkjent.first()
            assertThat(tilkjentYtelse.dagsats).isEqualTo(hendelse.avsluttetBehandling!!.tilkjentYtelse.perioder[0].dagsats)

            val sakRespons = ventPåSvar(
                {
                    dataSource.transaction {
                        SakstatistikkRepositoryImpl(it).hentAlleHendelserPåBehandling(
                            behandlingReferanse
                        )
                    }
                },
                { t -> t !== null && t.isNotEmpty() })

            assertThat(sakRespons).hasSize(3)
            assertThat(sakRespons!!.first().saksbehandler).isEqualTo("VEILEDER")
            assertThat(sakRespons.last().vedtakTidTrunkert).isEqualTo(
                LocalDateTime.parse("2025-10-06T13:56:38")
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

    private fun setupTestEnvironment(
        dataSource: DataSource,
        config: BigQueryConfig
    ): Pair<TestUtil, BigQueryClient> {
        val testUtil = TestUtil(dataSource, listOf("oppgave.retryFeilede"))
        val bigQueryClient = bigQueryClient(config)
        return testUtil to bigQueryClient
    }

    private fun prosesserHendelserOgVerifiserBehandling(
        dataSource: DataSource,
        url: String,
        client: RestClient<InputStream>,
        hendelser: List<HendelseData>,
        testUtil: TestUtil,
        expectedStatus: BehandlingStatus = BehandlingStatus.AVSLUTTET
    ): UUID {
        var referanse: UUID? = null
        var c = 1
        hendelser.forEach {
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
            logger.info("Hendelse nr ${c++}: ${it.data::class.simpleName} av ${hendelser.size}")
        }

        val feilende = dataSource.transaction { DriftJobbRepositoryExposed(it).hentAlleFeilende() }
        log.info("Feilende jobber: $feilende")
        testUtil.ventPåSvar()

        val behandling = ventPåSvar(
            { dataSource.transaction { BehandlingRepository(it).hent(referanse!!) } },
            { it != null }
        )

        assertThat(behandling!!.behandlingStatus()).isEqualTo(expectedStatus)
        return referanse!!
    }

    private fun hentSakstatistikkHendelser(
        dataSource: DataSource,
        referanse: UUID,
        minSize: Int = 0
    ) = ventPåSvar(
        {
            dataSource.transaction {
                SakstatistikkRepositoryImpl(it).hentAlleHendelserPåBehandling(referanse)
            }
        },
        { t -> t !== null && t.isNotEmpty() && t.size > minSize }
    )

    private fun hentSakstatistikkHendelserMedEksaktAntall(
        dataSource: DataSource,
        referanse: UUID,
        expectedSize: Int
    ) = ventPåSvar(
        {
            dataSource.transaction {
                SakstatistikkRepositoryImpl(it).hentAlleHendelserPåBehandling(referanse)
            }
        },
        { t -> t !== null && t.isNotEmpty() && t.size == expectedSize }
    )


}