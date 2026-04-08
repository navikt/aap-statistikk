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
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.sak.SakService
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.FakePdlGateway
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.konstruerSakstatistikkService
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
    fun `lagreMedOppgavedata skal ikke la oppgave-hendelse etter originalHendelsestid drive endretTid`(
        @Postgres dataSource: DataSource
    ) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingsflytTid = LocalDateTime.of(2024, 1, 1, 10, 0, 0)
        val oppgaveSendtTid = behandlingsflytTid.plusSeconds(30) // oppgave sent ETTER behandlingsflyt

        dataSource.transaction { conn ->
            val hendelsesService = HendelsesService(
                sakService = SakService(SakRepositoryImpl(conn)),
                avsluttetBehandlingService = mockk(relaxed = true),
                personService = PersonService(PersonRepository(conn)),
                meldekortRepository = MeldekortRepository(conn),
                opprettBigQueryLagringSakStatistikkCallback = {},
                behandlingService = BehandlingService(
                    BehandlingRepository(conn),
                    SkjermingService(FakePdlGateway(emptyMap()))
                ),
            )

            hendelsesService.prosesserNyHendelse(
                StoppetBehandling(
                    saksnummer = "BBBB",
                    sakStatus = SakStatus.UTREDES,
                    behandlingReferanse = behandlingReferanse,
                    relatertBehandling = null,
                    behandlingOpprettetTidspunkt = behandlingsflytTid,
                    mottattTid = behandlingsflytTid,
                    behandlingStatus = BehandlingStatus.UTREDES,
                    behandlingType = TypeBehandling.Førstegangsbehandling,
                    soknadsFormat = Kanal.DIGITAL,
                    ident = "1234567890",
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
                    identerForSak = listOf("1234567890")
                )
            )

            // Oppgave ankommer ETTER behandlingsflyt-hendelsen
            // Identifikator er oppgave-ID fra oppgave-appen (ekstern ID, ikke DB-rad-ID)
            val oppgaveIdentifikator = 42L
            val person = PersonRepository(conn).hentPerson("1234567890")!!
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
                    personIdent = "1234567890",
                    saksnummer = "BBBB",
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

            val behandlingId =
                BehandlingRepository(conn).hent(behandlingReferanse)!!.id()

            konstruerSakstatistikkService(conn).lagreMedOppgavedata(
                behandlingId,
                originalHendelsestid = behandlingsflytTid
            )

            val lagret = SakstatistikkRepositoryImpl(conn).hentAlleHendelserPåBehandling(behandlingReferanse)
            assertThat(lagret).hasSize(1)
            assertThat(lagret.first().endretTid)
                .describedAs("endretTid skal ikke overstige originalHendelsestid selv om oppgave ankommer senere")
                .isEqualTo(behandlingsflytTid)
        }
    }

    @Test
    @Disabled("Fix reverted - capping endretTid til oppdatertTidspunkt i lagreSakInfoTilBigquery forårsaker nye kollisjoner med gamle rader")
    fun `lagreSakInfoTilBigquery skal ikke la oppgave-hendelse etter behandlingens oppdatertTidspunkt drive endretTid`(
        @Postgres dataSource: DataSource
    ) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingsflytTid = LocalDateTime.of(2024, 1, 1, 10, 0, 0)
        val oppgaveSendtTid = behandlingsflytTid.plusSeconds(1) // oppgave ankommer ETTER behandling

        dataSource.transaction { conn ->
            val hendelsesService = HendelsesService(
                sakService = SakService(SakRepositoryImpl(conn)),
                avsluttetBehandlingService = mockk(relaxed = true),
                personService = PersonService(PersonRepository(conn)),
                meldekortRepository = MeldekortRepository(conn),
                opprettBigQueryLagringSakStatistikkCallback = {},
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

            konstruerSakstatistikkService(conn).lagreSakInfoTilBigquery(behandlingId)

            val lagret = SakstatistikkRepositoryImpl(conn).hentAlleHendelserPåBehandling(behandlingReferanse)
            assertThat(lagret.map { it.endretTid })
                .describedAs("endretTid skal ikke overstige behandlingens oppdatertTidspunkt selv om oppgave ankommer senere")
                .allSatisfy { assertThat(it).isBeforeOrEqualTo(behandlingsflytTid) }
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
                    sakService = SakService(SakRepositoryImpl(it)),
                    // mockk fordi irrelevant for denne testen
                    avsluttetBehandlingService = mockk(relaxed = true),
                    personService = PersonService(PersonRepository(it)),
                    meldekortRepository = MeldekortRepository(it),
                    opprettBigQueryLagringSakStatistikkCallback = {},
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
    }
}