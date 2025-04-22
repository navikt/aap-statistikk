package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegGruppe
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.enhet.EnhetRepository
import no.nav.aap.statistikk.oppgave.*
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.sak.tilSaksnummer
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.opprettTestPerson
import no.nav.aap.statistikk.testutils.opprettTestSak
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

class BehandlingRepositoryTest {
    @Test
    fun `sette inn og hente ut igjen`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789".let(::Saksnummer), person)

        val referanse = UUID.randomUUID()

        val enhet = dataSource.transaction {
            val enhetUtenId = Enhet(kode = "1337")
            enhetUtenId.copy(id = EnhetRepository(it).lagreEnhet(enhetUtenId))
        }
        dataSource.transaction {
            OppgaveRepository(it).lagreOppgave(
                Oppgave(
                    identifikator = 123,
                    avklaringsbehov = "1337",
                    enhet = enhet,
                    person = person,
                    status = Oppgavestatus.OPPRETTET,
                    behandlingReferanse = BehandlingReferanse(referanse = referanse),
                    opprettetTidspunkt = LocalDateTime.now(),
                    hendelser = listOf()
                )
            )
        }

        val vedtakstidspunkt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS)
        val clock = Clock.fixed(Instant.now(), ZoneId.of("Europe/Oslo"))
        dataSource.transaction {
            BehandlingRepository(
                it,
                clock = clock
            ).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    vedtakstidspunkt = vedtakstidspunkt,
                    ansvarligBeslutter = "Josgeir Dalføre",
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    sisteSaksbehandler = "Joark Jorgensen",
                    relaterteIdenter = listOf("123", "456", "123456789"),
                    gjeldendeAvklaringsBehov = "0559",
                    gjeldendeAvklaringsbehovStatus = Status.OPPRETTET,
                    venteÅrsak = "VENTER_PÅ_OPPLYSNINGER_FRA_UTENLANDSKE_MYNDIGHETER",
                    returÅrsak = "MANGELFULL_BEGRUNNELSE",
                    gjeldendeStegGruppe = StegGruppe.BREV,
                    resultat = ResultatKode.INNVILGET,
                    årsaker = listOf(ÅrsakTilBehandling.SØKNAD, ÅrsakTilBehandling.G_REGULERING)
                )
            )
        }

        val uthentet = dataSource.transaction { BehandlingRepository(it).hent(referanse) }

        uthentet!!
        assertThat(uthentet.status).isEqualTo(BehandlingStatus.UTREDES)
        assertThat(uthentet.sak.sakStatus).isEqualTo(SakStatus.UTREDES)
        assertThat(uthentet.relaterteIdenter).containsExactlyInAnyOrder("123", "456", "123456789")
        assertThat(uthentet.gjeldendeAvklaringsBehov).isEqualTo("0559")
        assertThat(uthentet.søknadsformat).isEqualTo(SøknadsFormat.PAPIR)
        assertThat(uthentet.venteÅrsak).isEqualTo("VENTER_PÅ_OPPLYSNINGER_FRA_UTENLANDSKE_MYNDIGHETER")
        assertThat(uthentet.gjeldendeStegGruppe).isEqualTo(StegGruppe.BREV)
        assertThat(uthentet.vedtakstidspunkt).isEqualTo(vedtakstidspunkt)
        assertThat(uthentet.ansvarligBeslutter).isEqualTo("Josgeir Dalføre")
        assertThat(uthentet.årsaker).containsExactlyInAnyOrder(
            ÅrsakTilBehandling.SØKNAD,
            ÅrsakTilBehandling.G_REGULERING
        )
        assertThat(uthentet.behandlendeEnhet).isEqualTo(enhet)
        assertThat(uthentet.gjeldendeAvklaringsbehovStatus).isEqualTo(Status.OPPRETTET)
        assertThat(uthentet.resultat).isEqualTo(ResultatKode.INNVILGET)
        assertThat(uthentet.hendelser).satisfiesExactly(
            {
                assertThat(it.tidspunkt).isCloseTo(
                    LocalDateTime.now(clock),
                    within(500, ChronoUnit.MILLIS)
                )
                assertThat(it.saksbehandler?.ident).isEqualTo("Joark Jorgensen")
                assertThat(it.avklaringsbehovStatus).isEqualTo(Status.OPPRETTET)
            },
        )
        assertThat(uthentet.returÅrsak).isEqualTo("MANGELFULL_BEGRUNNELSE")

        dataSource.transaction {
            BehandlingRepository(it).oppdaterBehandling(
                uthentet.copy(
                    venteÅrsak = "XXX"
                )
            )

            BehandlingRepository(it).oppdaterBehandling(
                uthentet.copy(
                    venteÅrsak = "ABC"
                )
            )
        }

        val uthentet2 = dataSource.transaction { BehandlingRepository(it).hent(uthentet.referanse) }

        assertThat(uthentet2!!.hendelser).isSortedAccordingTo { c1, c2 -> c1.tidspunkt.compareTo(c2.tidspunkt) }
        assertThat(uthentet2.hendelser.size).isEqualTo(3)
    }

    @Test
    fun `lagre to ganger med eksisterende versjon`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789".tilSaksnummer(), person)

        val referanse = UUID.randomUUID()
        dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    relaterteIdenter = listOf(),
                )
            )
        }

        val referanse2 = UUID.randomUUID()
        dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse2,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    relaterteIdenter = listOf(),
                )
            )
        }

        dataSource.transaction {
            assertThat(BehandlingRepository(it).hent(referanse)).isNotNull()
            assertThat(BehandlingRepository(it).hent(referanse2)).isNotNull()
        }
    }

    @Test
    fun `lagre oppdatert behandling, henter ut nyeste info`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789".tilSaksnummer(), person)

        val referanse = UUID.randomUUID()

        val behandlingId = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.OPPRETTET,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.PAPIR,
                )
            )
        }
        dataSource.transaction {
            BehandlingRepository(it).oppdaterBehandling(
                Behandling(
                    id = behandlingId,
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx2"),
                    søknadsformat = SøknadsFormat.DIGITAL,
                )
            )
        }

        dataSource.transaction {
            assertThat(BehandlingRepository(it).hent(referanse)).isNotNull()
        }
    }

    @Test
    fun `telle antall fullførte behandlinger`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789".tilSaksnummer(), person)

        val referanse = UUID.randomUUID()
        val referanse2 = UUID.randomUUID()

        val behandlingId = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.OPPRETTET,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.PAPIR,
                )
            )
        }
        dataSource.transaction {
            BehandlingRepository(it).oppdaterBehandling(
                Behandling(
                    id = behandlingId,
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.AVSLUTTET,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx2"),
                    søknadsformat = SøknadsFormat.PAPIR,
                )
            )
        }

        val behandlingId2 = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse2,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.OPPRETTET,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.DIGITAL,
                )
            )
        }
        dataSource.transaction {
            BehandlingRepository(it).oppdaterBehandling(
                Behandling(
                    id = behandlingId2,
                    referanse = referanse2,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.AVSLUTTET,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx2"),
                    søknadsformat = SøknadsFormat.DIGITAL,
                )
            )
        }

        val antall = dataSource.transaction {
            BehandlingRepository(it).tellFullførteBehandlinger()
        }

        assertThat(antall).isEqualTo(2)
    }

    @Test
    fun `prøve å sette inn flere med gjeldende = true skal feile`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789".tilSaksnummer(), person)

        val referanse = UUID.randomUUID()

        val id = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    relaterteIdenter = listOf("123", "456", "123456789"),
                    gjeldendeAvklaringsBehov = "0559",
                )
            )
        }

        assertThrows<Exception> {
            dataSource.transaction {
                it.execute("INSERT INTO behandling_historikk (behandling_id, versjon_id, gjeldende, oppdatert_tid, mottatt_tid, status, siste_saksbehandler, gjeldende_avklaringsbehov) VALUES (?,?,?,?,?,?,?,?)") {
                    setParams {
                        setLong(1, id.id)
                        setLong(2, 1)
                        setBoolean(3, true)
                        setLocalDateTime(4, LocalDateTime.now())
                        setLocalDateTime(5, LocalDateTime.now())
                        setString(6, BehandlingStatus.UTREDES.name)
                        setString(7, BehandlingStatus.UTREDES.name)
                        setString(8, BehandlingStatus.UTREDES.name)
                    }
                }
            }
        }
    }
}