package no.nav.aap.statistikk.saksstatistikk

import io.mockk.mockk
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.behandling.ÅrsakTilOpprettelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.meldekort.MeldekortRepository
import no.nav.aap.statistikk.enhet.Enhet
import no.nav.aap.statistikk.enhet.EnhetRepositoryImpl
import no.nav.aap.statistikk.oppgave.BehandlingReferanse
import no.nav.aap.statistikk.oppgave.HendelseType
import no.nav.aap.statistikk.oppgave.Oppgave
import no.nav.aap.statistikk.oppgave.OppgaveHendelse
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepositoryImpl
import no.nav.aap.statistikk.oppgave.OppgaveRepositoryImpl
import no.nav.aap.statistikk.oppgave.Oppgavestatus
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.sak.SakRepository
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.FakeHendelsePublisher
import no.nav.aap.statistikk.testutils.FakePdlGateway
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.konstruerSakstatistikkService
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.verdityper.dokument.Kanal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.function.BiPredicate
import javax.sql.DataSource
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as AvklaringsbehovStatus
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status as BehandlingStatus
import no.nav.aap.behandlingsflyt.kontrakt.sak.Status as SakStatus

class SaksStatistikkServiceTest {
    @Test
    @Disabled
    fun `hente ut historikk`(@Postgres dataSource: DataSource) {

        val referanse = lagreHendelser(dataSource)
        val behandlingId =
            dataSource.transaction { BehandlingRepository(it).hent(referanse)!!.id!! }

        val alleHendelser = dataSource.transaction {
            val sakStatikkService = konstruerSakstatistikkService(it)

            sakStatikkService.alleHendelserPåBehandling(behandlingId)
        }

        dataSource.transaction {
            konstruerSakstatistikkService(
                it
            ).lagreSakInfoTilBigquery(behandlingId)
        }

        val nedlagrete = dataSource.transaction {
            SakstatistikkRepositoryImpl(it).hentAlleHendelserPåBehandling(referanse)
        }
        assertThat(nedlagrete).hasSize(2)
        assertThat(alleHendelser.last())
            .usingRecursiveComparison()
            .ignoringFields("sekvensNummer")
            .withEqualsForFieldsMatchingRegexes(
                BiPredicate { a: LocalDateTime, b: LocalDateTime ->
                    Duration.between(a, b).abs() < Duration.ofSeconds(1)
                },
                "(endret|teknisk)Tid"
            )
            .isEqualTo(nedlagrete.last())

        assertThat(alleHendelser.last().ansvarligEnhetKode).describedAs(
            "Ansvarlig enhet should be set from oppgave. Last event: ${alleHendelser.last()}"
        ).isEqualTo("0220")
        assertThat(alleHendelser.map { it.endretTid }).doesNotHaveDuplicates()
        assertThat(alleHendelser.size).isEqualTo(2)
    }

    @Test
    fun `lagreBQBehandling skal bumpe endretTid slik at ny hendelse alltid har høyere endretTid enn forrige`(
        @Postgres dataSource: DataSource
    ) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingsflytTid = LocalDateTime.of(2024, 1, 1, 10, 0, 0)
        val oppgaveSendtTid = behandlingsflytTid.plusSeconds(1) // oppgave ankommer ETTER behandling

        dataSource.transaction { conn ->
            val hendelsesService = HendelsesService(
                sakRepository = SakRepositoryImpl(conn),
                avsluttetBehandlingService = mockk(relaxed = true),
                personRepository = PersonRepository(conn),
                meldekortRepository = MeldekortRepository(conn),
                hendelsePublisher = FakeHendelsePublisher(),
                behandlingService = BehandlingService(
                    BehandlingRepository(conn),
                    SkjermingService(FakePdlGateway(emptyMap()))
                ),
            )

            hendelsesService.prosesserNyHendelse(
                StoppetBehandling(
                    saksnummer = "CCCC",
                    sakStatus = SakStatus.UTREDES,
                    behandlingReferanse = behandlingReferanse,
                    relatertBehandling = null,
                    behandlingOpprettetTidspunkt = behandlingsflytTid,
                    mottattTid = behandlingsflytTid,
                    behandlingStatus = BehandlingStatus.UTREDES,
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    soknadsFormat = Kanal.DIGITAL,
                    ident = "1234567891",
                    versjon = "1",
                    vurderingsbehov = listOf(Vurderingsbehov.VURDER_RETTIGHETSPERIODE),
                    årsakTilOpprettelse = ÅrsakTilOpprettelse.SØKNAD,
                    avklaringsbehov = listOf(
                        AvklaringsbehovHendelseDto(
                            avklaringsbehovDefinisjon = Definisjon.VURDER_RETTIGHETSPERIODE,
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endringer = listOf(
                                EndringDTO(
                                    status = AvklaringsbehovStatus.OPPRETTET,
                                    tidsstempel = behandlingsflytTid,
                                    endretAv = "system",
                                )
                            ),
                        )
                    ),
                    hendelsesTidspunkt = behandlingsflytTid,
                    avsluttetBehandling = null,
                    identerForSak = listOf("1234567891")
                )
            )

            // Oppgave ankommer ETTER behandlingsflyt-hendelsen
            val oppgaveIdentifikator = 99L
            val person = PersonRepository(conn).hentPerson("1234567891")!!
            val enhetId = EnhetRepositoryImpl(conn).lagreEnhet(Enhet(null, "4491"))
            OppgaveRepositoryImpl(conn).lagreOppgave(
                Oppgave(
                    identifikator = oppgaveIdentifikator,
                    avklaringsbehov = Definisjon.VURDER_RETTIGHETSPERIODE.kode.name,
                    enhet = Enhet(enhetId, "4491"),
                    person = person,
                    status = Oppgavestatus.OPPRETTET,
                    opprettetTidspunkt = oppgaveSendtTid,
                    behandlingReferanse = BehandlingReferanse(referanse = behandlingReferanse),
                    hendelser = emptyList(),
                )
            )
            OppgaveHendelseRepositoryImpl(conn).lagreHendelse(
                OppgaveHendelse(
                    hendelse = HendelseType.OPPRETTET,
                    oppgaveId = oppgaveIdentifikator,
                    mottattTidspunkt = oppgaveSendtTid,
                    personIdent = "1234567891",
                    saksnummer = "CCCC",
                    behandlingRef = behandlingReferanse,
                    enhet = "4491",
                    avklaringsbehovKode = Definisjon.VURDER_RETTIGHETSPERIODE.kode.name,
                    status = Oppgavestatus.OPPRETTET,
                    reservertAv = null,
                    reservertTidspunkt = null,
                    opprettetTidspunkt = oppgaveSendtTid,
                    endretAv = null,
                    endretTidspunkt = null,
                    sendtTid = oppgaveSendtTid,
                    versjon = 1L,
                )
            )

            val behandlingId = BehandlingRepository(conn).hent(behandlingReferanse)!!.id()
            val service = konstruerSakstatistikkService(conn)

            // Simuler oppgave-trigget jobb: lagrer UTREDES-tilstand med endretTid = oppgaveSendtTid
            service.lagreSakInfoTilBigquery(behandlingId)

            val utredesRad = SakstatistikkRepositoryImpl(conn)
                .hentAlleHendelserPåBehandling(behandlingReferanse).last()
            assertThat(utredesRad.endretTid).isEqualTo(oppgaveSendtTid)

            // Simuler behandling-trigget jobb: AVSLUTTET med samme beregnede endretTid
            // (begge bruker oppgavens sendtTid) → skal bumpes til oppgaveSendtTid + 1000ns
            service.lagreBQBehandling(utredesRad.copy(behandlingStatus = "AVSLUTTET"))

            val alleRader = SakstatistikkRepositoryImpl(conn).hentAlleHendelserPåBehandling(behandlingReferanse)
            assertThat(alleRader.last().behandlingStatus)
                .describedAs("AVSLUTTET skal komme sist")
                .isEqualTo("AVSLUTTET")
            assertThat(alleRader.last().endretTid)
                .describedAs("AVSLUTTET skal ha høyere endretTid enn UTREDES etter bump")
                .isEqualTo(oppgaveSendtTid.plusNanos(1000))
        }
    }

    @Test
    fun `lagreBQBehandling skal lagre hendelse med eldre endretTid på riktig historisk posisjon`(
        @Postgres dataSource: DataSource
    ) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingsflytTid = LocalDateTime.of(2024, 1, 1, 10, 0, 0)
        val oppgaveSendtTid = behandlingsflytTid.plusSeconds(5)

        dataSource.transaction { conn ->
            val hendelsesService = HendelsesService(
                sakRepository = SakRepositoryImpl(conn),
                avsluttetBehandlingService = mockk(relaxed = true),
                personRepository = PersonRepository(conn),
                meldekortRepository = MeldekortRepository(conn),
                hendelsePublisher = FakeHendelsePublisher(),
                behandlingService = BehandlingService(
                    BehandlingRepository(conn),
                    SkjermingService(FakePdlGateway(emptyMap()))
                ),
            )

            hendelsesService.prosesserNyHendelse(
                StoppetBehandling(
                    saksnummer = "EEEE",
                    sakStatus = SakStatus.UTREDES,
                    behandlingReferanse = behandlingReferanse,
                    relatertBehandling = null,
                    behandlingOpprettetTidspunkt = behandlingsflytTid,
                    mottattTid = behandlingsflytTid,
                    behandlingStatus = BehandlingStatus.UTREDES,
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    soknadsFormat = Kanal.DIGITAL,
                    ident = "1234567893",
                    versjon = "1",
                    vurderingsbehov = listOf(Vurderingsbehov.VURDER_RETTIGHETSPERIODE),
                    årsakTilOpprettelse = ÅrsakTilOpprettelse.SØKNAD,
                    avklaringsbehov = listOf(
                        AvklaringsbehovHendelseDto(
                            avklaringsbehovDefinisjon = Definisjon.VURDER_RETTIGHETSPERIODE,
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endringer = listOf(
                                EndringDTO(
                                    status = AvklaringsbehovStatus.OPPRETTET,
                                    tidsstempel = behandlingsflytTid,
                                    endretAv = "system",
                                )
                            ),
                        )
                    ),
                    hendelsesTidspunkt = behandlingsflytTid,
                    avsluttetBehandling = null,
                    identerForSak = listOf("1234567893")
                )
            )

            val enhetId = EnhetRepositoryImpl(conn).lagreEnhet(Enhet(null, "4491"))
            OppgaveRepositoryImpl(conn).lagreOppgave(
                Oppgave(
                    identifikator = 88L,
                    avklaringsbehov = Definisjon.VURDER_RETTIGHETSPERIODE.kode.name,
                    enhet = Enhet(enhetId, "4491"),
                    person = PersonRepository(conn).hentPerson("1234567893")!!,
                    status = Oppgavestatus.OPPRETTET,
                    opprettetTidspunkt = oppgaveSendtTid,
                    behandlingReferanse = BehandlingReferanse(referanse = behandlingReferanse),
                    hendelser = emptyList(),
                )
            )
            OppgaveHendelseRepositoryImpl(conn).lagreHendelse(
                OppgaveHendelse(
                    hendelse = HendelseType.OPPRETTET,
                    oppgaveId = 88L,
                    mottattTidspunkt = oppgaveSendtTid,
                    personIdent = "1234567893",
                    saksnummer = "EEEE",
                    behandlingRef = behandlingReferanse,
                    enhet = "4491",
                    avklaringsbehovKode = Definisjon.VURDER_RETTIGHETSPERIODE.kode.name,
                    status = Oppgavestatus.OPPRETTET,
                    reservertAv = null,
                    reservertTidspunkt = null,
                    opprettetTidspunkt = oppgaveSendtTid,
                    endretAv = null,
                    endretTidspunkt = null,
                    sendtTid = oppgaveSendtTid,
                    versjon = 1L,
                )
            )

            val behandlingId = BehandlingRepository(conn).hent(behandlingReferanse)!!.id()
            val service = konstruerSakstatistikkService(conn)

            // Lagre hendelser for behandlingen (OPPRETTET + UNDER_BEHANDLING med endretTid = oppgaveSendtTid)
            service.lagreSakInfoTilBigquery(behandlingId)

            // Hent siste rad — dette er den "nyere" hendelsen vi vil bevare som gjeldende tilstand
            val nyesteRad = SakstatistikkRepositoryImpl(conn)
                .hentAlleHendelserPåBehandling(behandlingReferanse).last()

            // Simuler forsinket retry-jobb: hendelse med eldre endretTid (mellom OPPRETTET og UNDER_BEHANDLING)
            // og annen enhet (ikke duplikat av eksisterende rader).
            val stalTidspunkt = behandlingsflytTid.plusSeconds(2)
            val forsinketHendelse = nyesteRad.copy(
                ansvarligEnhetKode = "ANNEN_ENHET",
                endretTid = stalTidspunkt
            )
            service.lagreBQBehandling(forsinketHendelse)

            val alleRaderSortert = SakstatistikkRepositoryImpl(conn)
                .hentAlleHendelserPåBehandling(behandlingReferanse)
                .sortedBy { it.endretTid }

            // Forsinket hendelse skal lagres med sin opprinnelige endretTid
            assertThat(alleRaderSortert.any { it.endretTid == stalTidspunkt && it.ansvarligEnhetKode == "ANNEN_ENHET" })
                .describedAs("Forsinket hendelse skal være lagret med opprinnelig endretTid")
                .isTrue()
            // Gjeldende tilstand (høyeste endretTid) skal fortsatt være den nyeste hendelsen
            assertThat(alleRaderSortert.last().endretTid)
                .describedAs("Nyeste rad skal fortsatt ha oppgaveSendtTid som endretTid")
                .isEqualTo(oppgaveSendtTid)
        }
    }

    @Test
    fun `lagreBQBehandling er idempotent for forsinket hendelse som kjøres to ganger`(
        @Postgres dataSource: DataSource
    ) {
        val behandlingUUID = UUID.randomUUID()
        val t2 = LocalDateTime.of(2024, 1, 1, 10, 0, 10)
        val t0 = t2.minusSeconds(8)  // stale: eldre enn t2

        dataSource.transaction { conn ->
            val service = konstruerSakstatistikkService(conn)
            val repo = SakstatistikkRepositoryImpl(conn)

            // T2: første hendelse (inngangshendelse — registrertTid == endretTid → ingen ekstra OPPRETTET-rad)
            val nyesteHendelse = lagTestBQBehandling(
                behandlingUUID = behandlingUUID,
                endretTid = t2,
                registrertTid = t2,
                behandlingStatus = "UNDER_BEHANDLING",
            )
            service.lagreBQBehandling(nyesteHendelse)

            // T0: forsinket retry-jobb med eldre endretTid og ulik status
            val forsinketHendelse = lagTestBQBehandling(
                behandlingUUID = behandlingUUID,
                endretTid = t0,
                registrertTid = t2,
                behandlingStatus = "AVSLUTTET",
            )
            service.lagreBQBehandling(forsinketHendelse)  // første gang: lagres

            val antallEtterFørsteRetry = repo.hentAlleHendelserPåBehandling(behandlingUUID).size

            service.lagreBQBehandling(forsinketHendelse)  // andre gang: skal hoppes over

            val alleRader = repo.hentAlleHendelserPåBehandling(behandlingUUID)
            assertThat(alleRader)
                .describedAs("Gjentagende retry skal ikke lagre dobbel rad")
                .hasSize(antallEtterFørsteRetry)
            assertThat(alleRader.any { it.endretTid == t0 && it.behandlingStatus == "AVSLUTTET" })
                .describedAs("Forsinket hendelse skal ha blitt lagret med opprinnelig endretTid")
                .isTrue()
            assertThat(alleRader.maxBy { it.endretTid }.endretTid)
                .describedAs("Gjeldende tilstand skal fortsatt være t2")
                .isEqualTo(t2)
        }
    }

    @Test
    fun `retry etter ManglerEnhet ser allerede lagret rad som duplikat og lagrer ikke på nytt`(
        @Postgres dataSource: DataSource
    ) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingsflytTid = LocalDateTime.of(2024, 1, 1, 10, 0, 0)
        val oppgaveSendtTid = behandlingsflytTid.plusSeconds(5)

        dataSource.transaction { conn ->
            val hendelsesService = HendelsesService(
                sakRepository = SakRepositoryImpl(conn),
                avsluttetBehandlingService = mockk(relaxed = true),
                personRepository = PersonRepository(conn),
                meldekortRepository = MeldekortRepository(conn),
                hendelsePublisher = FakeHendelsePublisher(),
                behandlingService = BehandlingService(
                    BehandlingRepository(conn),
                    SkjermingService(FakePdlGateway(emptyMap()))
                ),
            )

            hendelsesService.prosesserNyHendelse(
                StoppetBehandling(
                    saksnummer = "DDDD",
                    sakStatus = SakStatus.UTREDES,
                    behandlingReferanse = behandlingReferanse,
                    relatertBehandling = null,
                    behandlingOpprettetTidspunkt = behandlingsflytTid,
                    mottattTid = behandlingsflytTid,
                    behandlingStatus = BehandlingStatus.UTREDES,
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    soknadsFormat = Kanal.DIGITAL,
                    ident = "1234567892",
                    versjon = "1",
                    vurderingsbehov = listOf(Vurderingsbehov.VURDER_RETTIGHETSPERIODE),
                    årsakTilOpprettelse = ÅrsakTilOpprettelse.SØKNAD,
                    avklaringsbehov = listOf(
                        AvklaringsbehovHendelseDto(
                            avklaringsbehovDefinisjon = Definisjon.VURDER_RETTIGHETSPERIODE,
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endringer = listOf(
                                EndringDTO(
                                    status = AvklaringsbehovStatus.OPPRETTET,
                                    tidsstempel = behandlingsflytTid,
                                    endretAv = "system",
                                )
                            ),
                        )
                    ),
                    hendelsesTidspunkt = behandlingsflytTid,
                    avsluttetBehandling = null,
                    identerForSak = listOf("1234567892")
                )
            )

            val oppgaveIdentifikator = 77L
            val person = PersonRepository(conn).hentPerson("1234567892")!!
            val enhetId = EnhetRepositoryImpl(conn).lagreEnhet(Enhet(null, "4491"))
            OppgaveRepositoryImpl(conn).lagreOppgave(
                Oppgave(
                    identifikator = oppgaveIdentifikator,
                    avklaringsbehov = Definisjon.VURDER_RETTIGHETSPERIODE.kode.name,
                    enhet = Enhet(enhetId, "4491"),
                    person = person,
                    status = Oppgavestatus.OPPRETTET,
                    opprettetTidspunkt = oppgaveSendtTid,
                    behandlingReferanse = BehandlingReferanse(referanse = behandlingReferanse),
                    hendelser = emptyList(),
                )
            )
            OppgaveHendelseRepositoryImpl(conn).lagreHendelse(
                OppgaveHendelse(
                    hendelse = HendelseType.OPPRETTET,
                    oppgaveId = oppgaveIdentifikator,
                    mottattTidspunkt = oppgaveSendtTid,
                    personIdent = "1234567892",
                    saksnummer = "DDDD",
                    behandlingRef = behandlingReferanse,
                    enhet = "4491",
                    avklaringsbehovKode = Definisjon.VURDER_RETTIGHETSPERIODE.kode.name,
                    status = Oppgavestatus.OPPRETTET,
                    reservertAv = null,
                    reservertTidspunkt = null,
                    opprettetTidspunkt = oppgaveSendtTid,
                    endretAv = null,
                    endretTidspunkt = null,
                    sendtTid = oppgaveSendtTid,
                    versjon = 1L,
                )
            )

            val behandlingId = BehandlingRepository(conn).hent(behandlingReferanse)!!.id()
            val service = konstruerSakstatistikkService(conn)

            // Oppgave-trigget jobb lagrer korrekt tilstand
            service.lagreSakInfoTilBigquery(behandlingId)
            val raderEtterFørsteKall =
                SakstatistikkRepositoryImpl(conn).hentAlleHendelserPåBehandling(behandlingReferanse).size

            // Retry (simulert) kaller lagreSakInfoTilBigquery igjen med nåværende tilstand
            // → skal se raden som duplikat og ikke lagre på nytt
            service.lagreSakInfoTilBigquery(behandlingId)

            val alleRader = SakstatistikkRepositoryImpl(conn).hentAlleHendelserPåBehandling(behandlingReferanse)
            assertThat(alleRader)
                .describedAs("Retryen skal ikke lagre en ekstra rad")
                .hasSize(raderEtterFørsteKall)
        }
    }

    companion object {

        fun lagreHendelser(dataSource: DataSource): UUID {
            val behandlingReferanse = UUID.randomUUID()
            val baseTid = LocalDateTime.now()
            val mottattTid = baseTid

            println(baseTid)

            dataSource.transaction {
                val hendelsesService = HendelsesService(
                    sakRepository = SakRepositoryImpl(it),
                    // mockk fordi irrelevant for denne testen
                    avsluttetBehandlingService = mockk(relaxed = true),
                    personRepository = PersonRepository(it),
                    meldekortRepository = MeldekortRepository(it),
                    hendelsePublisher = FakeHendelsePublisher(),
                    behandlingService = BehandlingService(
                        BehandlingRepository(it),
                        SkjermingService(FakePdlGateway(emptyMap()))
                    ),
                )

                hendelsesService.prosesserNyHendelse(
                    StoppetBehandling(
                        saksnummer = "AAAA",
                        sakStatus = SakStatus.UTREDES,
                        behandlingReferanse = behandlingReferanse,
                        relatertBehandling = null,
                        behandlingOpprettetTidspunkt = baseTid,
                        mottattTid = mottattTid,
                        behandlingStatus = BehandlingStatus.UTREDES,
                        behandlingType = TypeBehandling.Førstegangsbehandling,
                        soknadsFormat = Kanal.DIGITAL,
                        ident = "1233456",
                        versjon = "1",
                        vurderingsbehov = listOf(Vurderingsbehov.VURDER_RETTIGHETSPERIODE),
                        årsakTilOpprettelse = ÅrsakTilOpprettelse.SØKNAD,
                        avklaringsbehov = listOf(
                            AvklaringsbehovHendelseDto(
                                avklaringsbehovDefinisjon = Definisjon.VURDER_RETTIGHETSPERIODE,
                                status = AvklaringsbehovStatus.OPPRETTET,
                                endringer = listOf(
                                    EndringDTO(
                                        status = AvklaringsbehovStatus.OPPRETTET,
                                        tidsstempel = baseTid.plusSeconds(1),
                                        endretAv = "Bork",
                                    )
                                ),
                            )
                        ),
                        hendelsesTidspunkt = baseTid.plusSeconds(1),
                        avsluttetBehandling = null,
                        identerForSak = listOf("1233455", "11212")
                    )
                )

                hendelsesService.prosesserNyHendelse(
                    StoppetBehandling(
                        saksnummer = "AAAA",
                        sakStatus = SakStatus.UTREDES,
                        behandlingReferanse = behandlingReferanse,
                        relatertBehandling = null,
                        behandlingOpprettetTidspunkt = baseTid,
                        mottattTid = mottattTid,
                        behandlingStatus = BehandlingStatus.UTREDES,
                        behandlingType = TypeBehandling.Førstegangsbehandling,
                        soknadsFormat = Kanal.DIGITAL,
                        ident = "1233456",
                        versjon = "1",
                        vurderingsbehov = listOf(Vurderingsbehov.VURDER_RETTIGHETSPERIODE),
                        årsakTilOpprettelse = ÅrsakTilOpprettelse.SØKNAD,
                        avklaringsbehov = listOf(
                            AvklaringsbehovHendelseDto(
                                avklaringsbehovDefinisjon = Definisjon.VURDER_RETTIGHETSPERIODE,
                                status = AvklaringsbehovStatus.AVSLUTTET,
                                endringer = listOf(
                                    EndringDTO(
                                        status = AvklaringsbehovStatus.OPPRETTET,
                                        tidsstempel = baseTid.plusSeconds(1),
                                        endretAv = "Bork",
                                    ),
                                    EndringDTO(
                                        status = AvklaringsbehovStatus.AVSLUTTET,
                                        tidsstempel = baseTid.plusSeconds(3),
                                        endretAv = "Bork",
                                    )
                                ),
                            ),
                            AvklaringsbehovHendelseDto(
                                avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                                status = AvklaringsbehovStatus.OPPRETTET,
                                endringer = listOf(
                                    EndringDTO(
                                        status = AvklaringsbehovStatus.OPPRETTET,
                                        tidsstempel = baseTid.plusSeconds(3),
                                        endretAv = "Bjork",
                                    )
                                ),
                            ),
                        ),
                        hendelsesTidspunkt = baseTid.plusSeconds(3),
                        avsluttetBehandling = null,
                        identerForSak = listOf("1233455", "11212")
                    )
                )

                // Oppgave opprettet ETTER at AVKLAR_SYKDOM ble gjeldende
                OppgaveHendelseRepositoryImpl(it).lagreHendelse(
                    OppgaveHendelse(
                        hendelse = HendelseType.OPPRETTET,
                        oppgaveId = 1L,
                        mottattTidspunkt = baseTid.plusSeconds(4),
                        personIdent = "12345678910",
                        saksnummer = "123456789",
                        behandlingRef = behandlingReferanse,
                        enhet = "0220",
                        avklaringsbehovKode = Definisjon.AVKLAR_SYKDOM.kode.name,
                        status = Oppgavestatus.OPPRETTET,
                        reservertAv = "123456789",
                        reservertTidspunkt = baseTid.plusSeconds(4),
                        opprettetTidspunkt = baseTid.plusSeconds(4),
                        endretAv = "123456789",
                        endretTidspunkt = baseTid.plusSeconds(4),
                        sendtTid = baseTid.plusSeconds(4),
                        versjon = 1L,
                    )
                )
            }

            return behandlingReferanse
        }

        fun lagTestBQBehandling(
            behandlingUUID: UUID = UUID.randomUUID(),
            endretTid: LocalDateTime = LocalDateTime.now(),
            registrertTid: LocalDateTime = endretTid,
            behandlingStatus: String = "UNDER_BEHANDLING",
        ) = BQBehandling(
            behandlingUUID = behandlingUUID,
            behandlingType = "FØRSTEGANGSBEHANDLING",
            aktorId = "12345678901",
            saksnummer = "TESTSAKSNR",
            tekniskTid = LocalDateTime.now(),
            registrertTid = registrertTid,
            endretTid = endretTid,
            versjon = "v1",
            mottattTid = registrertTid,
            opprettetAv = "Kelvin",
            ansvarligBeslutter = null,
            søknadsFormat = SøknadsFormat.DIGITAL,
            saksbehandler = null,
            behandlingMetode = BehandlingMetode.MANUELL,
            behandlingStatus = behandlingStatus,
            behandlingÅrsak = "SØKNAD",
            resultatBegrunnelse = null,
            ansvarligEnhetKode = "4491",
            sakYtelse = "AAP",
            erResending = false,
        )
    }
}
