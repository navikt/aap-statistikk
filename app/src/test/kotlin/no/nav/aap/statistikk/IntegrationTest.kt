package no.nav.aap.statistikk

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.*
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.retry.DriftJobbRepositoryExposed
import no.nav.aap.motor.testutil.TestUtil
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.statistikk.OppgaveTilStatistikkDto
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import no.nav.aap.statistikk.api.stringToNumber
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.hendelser.påTidspunkt
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobb
import no.nav.aap.statistikk.oppgave.OppgaveHendelse
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepositoryImpl
import no.nav.aap.statistikk.saksstatistikk.BehandlingMetode
import no.nav.aap.statistikk.saksstatistikk.SakstatistikkRepositoryImpl
import no.nav.aap.statistikk.testutils.*
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.Vilkårtype
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status as BStatus
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

        val testUtil = setupTestEnvironment(dataSource)
        lateinit var referanse: UUID
        testKlientNoInjection(
            dbConfig,
            azureConfig = azureConfig,
        ) {
            referanse = prosesserHendelserOgVerifiserBehandling(
                dataSource, hendelserFraDBDump, testUtil
            )
        }

        // Sekvensnummer økes med 1 med ny info på sak
        val bqSaker2 = hentSakstatistikkHendelser(dataSource, referanse, minSize = 2)
//        assertThat(bqSaker2!!).hasSize(hendelserFraDBDump.size)
        assertThat(bqSaker2!!.map { it.ansvarligEnhetKode }).contains(
            "4491",
            "5701",
            "5700",
            "4491",
            "5701",
            "5700",
            "5701"
        )
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
        val avsluttede = bqSaker2.filter { it.behandlingStatus == "AVSLUTTET" }
            .groupBy { it.endretTid }
            .mapValues { it.value.last() }
            .values.sortedBy { it.endretTid }
        assertThat(avsluttede).hasSize(1)
        assertThat(avsluttede.onlyOrNull()?.vedtakTid).isNotNull
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
        ) {
            oppdatertBehandlingHendelse(avsluttetBehandlingHendelser.last().data)
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
                    LagreOppgaveHendelseJobb()
                ).medPayload(
                    DefaultJsonMapper.toJson(hendelse)
                ).forSak(stringToNumber(hendelse.saksnummer!!))
            )
        }

    }

    @Test
    fun `dump av hendelser for klage`(
        @Postgres dbConfig: DbConfig,
        @Postgres dataSource: DataSource,
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

        val testUtil = setupTestEnvironment(dataSource)
        lateinit var referanse: UUID
        testKlientNoInjection(
            dbConfig,
            azureConfig = azureConfig,
            FakeBigQueryClient,
        ) {
            val behandlingHendelser = hendelserFraFlyt.map {
                BehandlingHendelseData(it, LocalDateTime.now())
            }
            referanse = prosesserHendelserOgVerifiserBehandling(
                dataSource, behandlingHendelser, testUtil
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
    fun `test riktig kontor`(
        @Postgres dbConfig: DbConfig,
        @Postgres dataSource: DataSource,
        @Fakes azureConfig: AzureConfig,
    ) {
        val testUtil = setupTestEnvironment(dataSource)

        val behandlingReferanse = UUID.fromString("ca0a378d-9249-47b3-808a-afe6a6357ac5")
        val personIdent = "2718281828"
        val saksnummer = "ABCDE"

        val behandlingOpprettetTid = LocalDateTime.now()
        val behovOpprettetTid = LocalDateTime.now()
        val mottattTid = LocalDateTime.now()

        val initialBehandlingHendelse = StoppetBehandling(
            saksnummer = saksnummer,
            sakStatus = no.nav.aap.behandlingsflyt.kontrakt.sak.Status.UTREDES,
            behandlingReferanse = behandlingReferanse,
            behandlingOpprettetTidspunkt = behandlingOpprettetTid,
            mottattTid = mottattTid,
            tidspunktSisteEndring = LocalDateTime.now(),
            behandlingStatus = BStatus.UTREDES,
            behandlingType = TypeBehandling.Førstegangsbehandling,
            ident = personIdent,
            versjon = UUID.randomUUID().toString(),
            vurderingsbehov = listOf(Vurderingsbehov.SØKNAD),
            årsakTilOpprettelse = "SØKNAD",
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    id = 0L,
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                            tidsstempel = behovOpprettetTid,
                            endretAv = "Kelvin",
                        )
                    )
                )
            ),
            hendelsesTidspunkt = LocalDateTime.now(),
            avsluttetBehandling = null,
            identerForSak = listOf(personIdent),
            opprettetAv = null,
            nyeMeldekort = emptyList(),
            søknadIder = emptyList()
        )

        fun verifiserHendelseRekkefølge(
            expectedValues: List<Triple<String?, String?, BehandlingMetode>>
        ) {
            testUtil.ventPåSvar()
            val alleSakstatistikkHendelser = dataSource.transaction {
                SakstatistikkRepositoryImpl(it).hentAlleHendelserPåBehandling(behandlingReferanse)
            }

            println(alleSakstatistikkHendelser)
            println(alleSakstatistikkHendelser.last())
            println(alleSakstatistikkHendelser[alleSakstatistikkHendelser.size - 2])

            assertThat(alleSakstatistikkHendelser)
                .extracting("ansvarligEnhetKode", "saksbehandler", "behandlingMetode")
                .containsExactly(
                    *expectedValues.map { (enhet, saksbehandler, metode) ->
                        tuple(enhet, saksbehandler, metode)
                    }.toTypedArray()
                )
        }

        fun opprettOppgaveDto(
            oppgaveId: Long,
            enhet: String,
            avklaringsbehovKode: Definisjon,
            status: Status,
            reservertAv: String? = null,
            endretAv: String? = null
        ) = OppgaveTilStatistikkDto(
            id = oppgaveId,
            personIdent = personIdent,
            saksnummer = saksnummer,
            behandlingRef = behandlingReferanse,
            journalpostId = null,
            enhet = enhet,
            avklaringsbehovKode = avklaringsbehovKode.kode.name,
            status = status,
            behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
            reservertAv = reservertAv,
            reservertTidspunkt = if (reservertAv != null) LocalDateTime.now() else null,
            opprettetAv = "Kelvin",
            opprettetTidspunkt = LocalDateTime.now(),
            endretAv = endretAv,
            endretTidspunkt = if (endretAv != null) LocalDateTime.now() else null,
            versjon = if (reservertAv != null) 2 else 1,
            harHasteMarkering = false
        )

        testKlientNoInjection(dbConfig, azureConfig = azureConfig) {
            postBehandlingsflytHendelse(initialBehandlingHendelse)

            verifiserHendelseRekkefølge(
                listOf(
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple(null, null, BehandlingMetode.MANUELL)
                )
            )

            // Oppgave for sykdom opprettes
            postOppgaveData(
                no.nav.aap.oppgave.statistikk.OppgaveHendelse(
                    hendelse = HendelseType.OPPRETTET,
                    oppgaveTilStatistikkDto = opprettOppgaveDto(
                        oppgaveId = 123L,
                        enhet = "0401",
                        avklaringsbehovKode = Definisjon.AVKLAR_SYKDOM,
                        status = Status.OPPRETTET
                    )
                )
            )

            verifiserHendelseRekkefølge(
                listOf(
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple("0401", null, BehandlingMetode.MANUELL)
                )
            )

            // Oppgave reserveres av saksbehandler
            postOppgaveData(
                no.nav.aap.oppgave.statistikk.OppgaveHendelse(
                    hendelse = HendelseType.RESERVERT,
                    oppgaveTilStatistikkDto = opprettOppgaveDto(
                        oppgaveId = 123L,
                        enhet = "0401",
                        avklaringsbehovKode = Definisjon.AVKLAR_SYKDOM,
                        status = Status.OPPRETTET,
                        reservertAv = "Kompanjong Korrodheid",
                        endretAv = "Kompanjong Korrodheid"
                    )
                )
            )

            verifiserHendelseRekkefølge(
                listOf(
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple("0401", null, BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.MANUELL)
                )
            )

            // Sykdomsbehov løses og går til kvalitetssikring
            val hendelseMedKvalitetssikring = initialBehandlingHendelse.nyHendelse().copy(
                avklaringsbehov = initialBehandlingHendelse.avklaringsbehov
                    .løs(Definisjon.AVKLAR_SYKDOM, "Kompanjong Korrodheid")
                    .leggTilBehov(Definisjon.KVALITETSSIKRING)
            )
            postBehandlingsflytHendelse(hendelseMedKvalitetssikring)

            verifiserHendelseRekkefølge(
                listOf(
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple("0401", null, BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.KVALITETSSIKRING)
                )
            )

            // Sykdomsoppgave lukkes
            postOppgaveData(
                no.nav.aap.oppgave.statistikk.OppgaveHendelse(
                    hendelse = HendelseType.LUKKET,
                    oppgaveTilStatistikkDto = opprettOppgaveDto(
                        oppgaveId = 123L,
                        enhet = "0401",
                        avklaringsbehovKode = Definisjon.AVKLAR_SYKDOM,
                        status = Status.AVSLUTTET,
                        reservertAv = "Kompanjong Korrodheid",
                        endretAv = "Kompanjong Korrodheid"
                    )
                )
            )

            verifiserHendelseRekkefølge(
                listOf(
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple("0401", null, BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.KVALITETSSIKRING)
                )
            )

            // Oppgave for kvalitetssikring opprettes
            postOppgaveData(
                no.nav.aap.oppgave.statistikk.OppgaveHendelse(
                    hendelse = HendelseType.OPPRETTET,
                    oppgaveTilStatistikkDto = opprettOppgaveDto(
                        oppgaveId = 124L,
                        enhet = "0400",
                        avklaringsbehovKode = Definisjon.KVALITETSSIKRING,
                        status = Status.OPPRETTET
                    )
                )
            )

            verifiserHendelseRekkefølge(
                listOf(
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple("0401", null, BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.KVALITETSSIKRING),
                    Triple("0401", null, BehandlingMetode.KVALITETSSIKRING) // FEIL, bør være 0400
                )
            )

            // Kvalitetssikrers oppgave reserveres
            postOppgaveData(
                no.nav.aap.oppgave.statistikk.OppgaveHendelse(
                    hendelse = HendelseType.RESERVERT,
                    oppgaveTilStatistikkDto = opprettOppgaveDto(
                        oppgaveId = 124L,
                        enhet = "0400",
                        avklaringsbehovKode = Definisjon.KVALITETSSIKRING,
                        status = Status.OPPRETTET,
                        reservertAv = "Kvaliguy",
                        endretAv = "Kelvin"
                    )
                )
            )

            verifiserHendelseRekkefølge(
                listOf(
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple("0401", null, BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.KVALITETSSIKRING),
                    Triple("0401", null, BehandlingMetode.KVALITETSSIKRING), // FEIL, bør være 0400
                    Triple(
                        "0401",
                        "Kvaliguy",
                        BehandlingMetode.KVALITETSSIKRING
                    ) // FEIL, bør være 0400
                )
            )

            // Kvalitetssikring fullføres og går til beslutter
            val hendelseMedBeslutter = hendelseMedKvalitetssikring.nyHendelse().copy(
                avklaringsbehov = hendelseMedKvalitetssikring.avklaringsbehov
                    .løs(Definisjon.KVALITETSSIKRING, "Kvaliguy")
                    .leggTilBehov(Definisjon.FATTE_VEDTAK)
            )
            postBehandlingsflytHendelse(hendelseMedBeslutter)

            verifiserHendelseRekkefølge(
                listOf(
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple("0401", null, BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.KVALITETSSIKRING),
                    Triple("0401", null, BehandlingMetode.KVALITETSSIKRING), // FEIL, bør være 0400
                    Triple(
                        "0401",
                        "Kvaliguy",
                        BehandlingMetode.KVALITETSSIKRING
                    ), // FEIL, bør være 0400
                    Triple(
                        "0400",
                        "Kvaliguy",
                        BehandlingMetode.FATTE_VEDTAK
                    )  // FEIL, bør være null
                )
            )

            // Beslutteroppgave opprettes
            postOppgaveData(
                no.nav.aap.oppgave.statistikk.OppgaveHendelse(
                    hendelse = HendelseType.OPPRETTET,
                    oppgaveTilStatistikkDto = opprettOppgaveDto(
                        oppgaveId = 127L,
                        enhet = "4491",
                        avklaringsbehovKode = Definisjon.FATTE_VEDTAK,
                        status = Status.OPPRETTET
                    )
                )
            )

            verifiserHendelseRekkefølge(
                listOf(
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple("0401", null, BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.KVALITETSSIKRING),
                    Triple("0401", null, BehandlingMetode.KVALITETSSIKRING), // FEIL, bør være 0400
                    Triple(
                        "0401",
                        "Kvaliguy",
                        BehandlingMetode.KVALITETSSIKRING
                    ), // FEIL, bør være 0400
                    Triple(
                        "0400",
                        "Kvaliguy",
                        BehandlingMetode.FATTE_VEDTAK
                    ),  // FEIL, bør være null
                    Triple("0400", null, BehandlingMetode.FATTE_VEDTAK)  // FEIL, bør være 4491
                )
            )

            // Beslutteroppgave reserveres
            postOppgaveData(
                no.nav.aap.oppgave.statistikk.OppgaveHendelse(
                    hendelse = HendelseType.RESERVERT,
                    oppgaveTilStatistikkDto = opprettOppgaveDto(
                        oppgaveId = 127L,
                        enhet = "4491",
                        avklaringsbehovKode = Definisjon.FATTE_VEDTAK,
                        status = Status.OPPRETTET,
                        reservertAv = "Besluttersen",
                        endretAv = "Besluttersen"
                    )
                )
            )

            verifiserHendelseRekkefølge(
                listOf(
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple("0401", null, BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.KVALITETSSIKRING),
                    Triple("0401", null, BehandlingMetode.KVALITETSSIKRING), // FEIL, bør være 0400
                    Triple(
                        "0401",
                        "Kvaliguy",
                        BehandlingMetode.KVALITETSSIKRING
                    ), // FEIL, bør være 0400
                    Triple(
                        "0400",
                        "Kvaliguy",
                        BehandlingMetode.FATTE_VEDTAK
                    ),  // FEIL, bør være null
                    Triple("0400", null, BehandlingMetode.FATTE_VEDTAK),  // FEIL, bør være 4491
                    Triple(
                        "0400",
                        "Besluttersen",
                        BehandlingMetode.FATTE_VEDTAK
                    )  // FEIL, bør være 4491
                )
            )

            // Vedtak fattes og behandling går til iverksettelse
            val hendelseIverksettes = hendelseMedBeslutter
                .nyHendelse(BStatus.IVERKSETTES).copy(
                    avklaringsbehov = hendelseMedBeslutter.avklaringsbehov
                        .løs(Definisjon.FATTE_VEDTAK, "Besluttersen")
                        .leggTilBehov(Definisjon.SKRIV_VEDTAKSBREV)
                )
            postBehandlingsflytHendelse(hendelseIverksettes)

            verifiserHendelseRekkefølge(
                listOf(
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple("0401", null, BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.KVALITETSSIKRING),
                    Triple("0401", null, BehandlingMetode.KVALITETSSIKRING), // FEIL, bør være 0400
                    Triple(
                        "0401",
                        "Kvaliguy",
                        BehandlingMetode.KVALITETSSIKRING
                    ), // FEIL, bør være 0400
                    Triple(
                        "0400",
                        "Kvaliguy",
                        BehandlingMetode.FATTE_VEDTAK
                    ),  // FEIL, bør være null
                    Triple("0400", null, BehandlingMetode.FATTE_VEDTAK),  // FEIL, bør være 4491
                    Triple(
                        "0400",
                        "Besluttersen",
                        BehandlingMetode.FATTE_VEDTAK
                    ),  // FEIL, bør være 4491
                    Triple(
                        "4491",
                        "Besluttersen",
                        BehandlingMetode.MANUELL
                    ) // bør være 4491 - null, MANUELL
                )
            )

            // Beslutteroppgave lukkes
            postOppgaveData(
                no.nav.aap.oppgave.statistikk.OppgaveHendelse(
                    hendelse = HendelseType.LUKKET,
                    oppgaveTilStatistikkDto = opprettOppgaveDto(
                        oppgaveId = 127L,
                        enhet = "4491",
                        avklaringsbehovKode = Definisjon.FATTE_VEDTAK,
                        status = Status.AVSLUTTET,
                        endretAv = "Kelvin"
                    ).copy(versjon = 2, reservertAv = null, reservertTidspunkt = null)
                )
            )

            verifiserHendelseRekkefølge(
                listOf(
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple("0401", null, BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.KVALITETSSIKRING),
                    Triple("0401", null, BehandlingMetode.KVALITETSSIKRING), // FEIL, bør være 0400
                    Triple(
                        "0401",
                        "Kvaliguy",
                        BehandlingMetode.KVALITETSSIKRING
                    ), // FEIL, bør være 0400
                    Triple(
                        "0400",
                        "Kvaliguy",
                        BehandlingMetode.FATTE_VEDTAK
                    ),  // FEIL, bør være null
                    Triple("0400", null, BehandlingMetode.FATTE_VEDTAK),  // FEIL, bør være 4491
                    Triple(
                        "0400",
                        "Besluttersen",
                        BehandlingMetode.FATTE_VEDTAK
                    ),  // FEIL, bør være 4491
                    Triple(
                        "4491",
                        "Besluttersen",
                        BehandlingMetode.MANUELL
                    ), // bør være 4491 - null, MANUELL
                    Triple("4491", null, BehandlingMetode.MANUELL) // bør være 4491 - null, MANUELL
                )
            )

            // Vedtaksbrev sendes og behandling avsluttes
            val hendelseAvsluttet = hendelseIverksettes
                .nyHendelse(BStatus.AVSLUTTET).copy(
                    avklaringsbehov = hendelseMedBeslutter.avklaringsbehov.løs(
                        Definisjon.BESTILL_BREV,
                        "Brev-Besluttersen"
                    )
                )
            postBehandlingsflytHendelse(hendelseAvsluttet)

            verifiserHendelseRekkefølge(
                listOf(
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple(null, null, BehandlingMetode.MANUELL),
                    Triple("0401", null, BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.MANUELL),
                    Triple("0401", "Kompanjong Korrodheid", BehandlingMetode.KVALITETSSIKRING),
                    Triple("0401", null, BehandlingMetode.KVALITETSSIKRING), // FEIL, bør være 0400
                    Triple(
                        "0401",
                        "Kvaliguy",
                        BehandlingMetode.KVALITETSSIKRING
                    ), // FEIL, bør være 0400
                    Triple(
                        "0400",
                        "Kvaliguy",
                        BehandlingMetode.FATTE_VEDTAK
                    ),  // FEIL, bør være null
                    Triple("0400", null, BehandlingMetode.FATTE_VEDTAK),  // FEIL, bør være 4491
                    Triple(
                        "0400",
                        "Besluttersen",
                        BehandlingMetode.FATTE_VEDTAK
                    ),  // FEIL, bør være 4491
                    Triple(
                        "4491",
                        "Besluttersen",
                        BehandlingMetode.MANUELL
                    ), // bør være 4491 - null, MANUELL
                    Triple("4491", null, BehandlingMetode.MANUELL), // bør være 4491 - null, MANUELL
                    Triple("4491", null, BehandlingMetode.MANUELL) // bør være 4491 - null, MANUELL
                )
            )
        }
    }

    fun StoppetBehandling.nyHendelse(behandlingStatus: BStatus? = null) = this.copy(
        avsluttetBehandling = if (behandlingStatus == BStatus.AVSLUTTET) AvsluttetBehandlingDTO(
            tilkjentYtelse = TilkjentYtelseDTO(
                listOf()
            ),
            vilkårsResultat = VilkårsResultatDTO(
                typeBehandling = TypeBehandling.Førstegangsbehandling,
                listOf()
            ),
            beregningsGrunnlag = null,
            diagnoser = null,
            rettighetstypePerioder = listOf(),
            resultat = null,
            vedtakstidspunkt = null,
            fritaksvurderinger = null,
            perioderMedArbeidsopptrapping = listOf()
        ) else null,
        tidspunktSisteEndring = LocalDateTime.now(),
        behandlingStatus = behandlingStatus ?: this.behandlingStatus,
        versjon = UUID.randomUUID().toString(),
        hendelsesTidspunkt = LocalDateTime.now(),
    )

    fun List<AvklaringsbehovHendelseDto>.løs(
        definisjon: Definisjon,
        endretAv: String
    ): List<AvklaringsbehovHendelseDto> {
        return map { avklaringsbehov ->
            if (avklaringsbehov.avklaringsbehovDefinisjon == definisjon) {
                avklaringsbehov.copy(
                    status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                    endringer = avklaringsbehov.endringer + EndringDTO(
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                        tidsstempel = LocalDateTime.now(),
                        endretAv = endretAv,
                    )
                )
            } else avklaringsbehov
        }
    }

    fun List<AvklaringsbehovHendelseDto>.leggTilBehov(definisjon: Definisjon): List<AvklaringsbehovHendelseDto> {
        return this + listOf(
            AvklaringsbehovHendelseDto(
                id = this.size.toLong(),
                avklaringsbehovDefinisjon = definisjon,
                status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                endringer = listOf(
                    EndringDTO(
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        tidsstempel = LocalDateTime.now(),
                        endretAv = "Kelvin",
                    )
                )
            )
        )
    }

    @Test
    fun `test flyt`(
        @Postgres dbConfig: DbConfig,
        @Postgres dataSource: DataSource,
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
            behandlingStatus = BStatus.UTREDES,
            avsluttetBehandling = null,
            avklaringsbehov = hendelse.avklaringsbehov.påTidspunkt(midtI),
            hendelsesTidspunkt = hendelse.hendelsesTidspunkt.minusMinutes(5)
        )

        val testUtil = TestUtil(dataSource, listOf("oppgave.retryFeilede"))
        testKlientNoInjection(
            dbConfig,
            azureConfig = azureConfig
        ) {
            postBehandlingsflytHendelse(hendelsx)

            testUtil.ventPåSvar()

            val gjeldendeAVklaringsbehov =
                dataSource.transaction { BehandlingRepository(it).hent(hendelse.behandlingReferanse)!!.gjeldendeAvklaringsBehov }!!
                    .let { Definisjon.forKode(it) }

            postOppgaveData(
                no.nav.aap.oppgave.statistikk.OppgaveHendelse(
                    hendelse = HendelseType.OPPRETTET,
                    oppgaveTilStatistikkDto = OppgaveTilStatistikkDto(
                        id = 1,
                        personIdent = hendelse.ident,
                        saksnummer = hendelse.saksnummer,
                        behandlingRef = hendelse.behandlingReferanse,
                        enhet = "0400",
                        avklaringsbehovKode = gjeldendeAVklaringsbehov.kode.name,
                        status = Status.OPPRETTET,
                        behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
                        reservertAv = "Karl Korrodheid",
                        reservertTidspunkt = midtI,
                        opprettetAv = "Kelvin",
                        opprettetTidspunkt = midtI,
                        endretTidspunkt = midtI
                    )
                )
            )
            testUtil.ventPåSvar()

            postBehandlingsflytHendelse(hendelse)

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
                dataSource, behandling!!.referanse, expectedSize = 4
            )
            assertThat(bqSaker).isNotNull
            assertThat(bqSaker).hasSize(4)
            assertThat(bqSaker!!.first().sekvensNummer).isEqualTo(1)

            postBehandlingsflytHendelse(
                hendelse.copy(
                    behandlingStatus = BStatus.AVSLUTTET,
                    avsluttetBehandling = hendelse.avsluttetBehandling
                )
            )

            testUtil.ventPåSvar()

            // Sekvensnummer økes med 1 med ny info på sak
            val bqSaker2 = dataSource.transaction {
                SakstatistikkRepositoryImpl(it).hentAlleHendelserPåBehandling(behandlingReferanse)
            }
            assertThat(bqSaker2).hasSize(4)
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

            assertThat(sakRespons).hasSize(4)
            sakRespons!!.forEach { println(it) }
            assertThat(sakRespons.first().saksbehandler).isEqualTo("VEILEDER")
            assertThat(sakRespons).anyMatch { it.vedtakTidTrunkert != null }
            assertThat(sakRespons.last().vedtakTidTrunkert).isEqualTo(
                LocalDateTime.parse("2025-10-06T13:56:38")
            )
        }
    }

    private fun setupTestEnvironment(
        dataSource: DataSource
    ): TestUtil {
        val testUtil = TestUtil(dataSource, listOf("oppgave.retryFeilede"))
        return testUtil
    }

    private fun TestClient.prosesserHendelserOgVerifiserBehandling(
        dataSource: DataSource,
        hendelser: List<HendelseData>,
        testUtil: TestUtil,
        expectedStatus: BehandlingStatus = BehandlingStatus.AVSLUTTET
    ): UUID {
        var referanse: UUID? = null
        var c = 1
        hendelser.forEach {
            when (it) {
                is BehandlingHendelseData -> {
                    postBehandlingsflytHendelse(it.data)
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