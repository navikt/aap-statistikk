package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegGruppe
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.enhet.Enhet
import no.nav.aap.statistikk.enhet.EnhetRepositoryImpl
import no.nav.aap.statistikk.oppgave.BehandlingReferanse
import no.nav.aap.statistikk.oppgave.Oppgave
import no.nav.aap.statistikk.oppgave.OppgaveRepositoryImpl
import no.nav.aap.statistikk.oppgave.Oppgavestatus
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.sak.tilSaksnummer
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.builders.opprettTestPerson
import no.nav.aap.statistikk.testutils.builders.opprettTestSak
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

class BehandlingRepositoryTest {
    @Test
    fun `sette inn og hente ut igjen`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, Ident("123456789"))
        val relaterteIdenter = listOf("123", "456", "123456789").map(::Ident)
        val sak = opprettTestSak(dataSource, "123456789".let(::Saksnummer), person)

        val referanse = UUID.randomUUID()

        val enhet = dataSource.transaction {
            val enhetUtenId = Enhet(kode = "1337")
            enhetUtenId.copy(id = EnhetRepositoryImpl(it).lagreEnhet(enhetUtenId))
        }
        dataSource.transaction {
            OppgaveRepositoryImpl(it).lagreOppgave(
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

        val behandling = Behandling(
            referanse = referanse,
            sak = sak,
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            status = BehandlingStatus.UTREDES,
            opprettetTid = LocalDateTime.now(),
            mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
            vedtakstidspunkt = vedtakstidspunkt,
            relatertBehandlingReferanse = "REFERANSE",
            ansvarligBeslutter = "Josgeir Dalføre",
            versjon = Versjon("xxx"),
            søknadsformat = SøknadsFormat.PAPIR,
            sisteSaksbehandler = "Joark Jorgensen",
            relaterteIdenter = relaterteIdenter,
            gjeldendeAvklaringsBehov = Definisjon.AVKLAR_OVERGANG_ARBEID,
            gjeldendeAvklaringsbehovStatus = Status.OPPRETTET,
            sisteLøsteAvklaringsbehov = Definisjon.ARBEIDSOPPTRAPPING,
            sisteSaksbehandlerSomLøstebehov = "Z123354",
            sistLøsteAvklaringsbehovTidspunkt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
            venteÅrsak = "VENTER_PÅ_OPPLYSNINGER_FRA_UTENLANDSKE_MYNDIGHETER",
            returÅrsak = "MANGELFULL_BEGRUNNELSE",
            returÅrsakkoblinger = listOf(
                ReturÅrsakkobling(
                    Definisjon.AVKLAR_SYKDOM,
                    listOf("MANGELFULL_BEGRUNNELSE", "MANGLENDE_UTREDNING")
                ),
                ReturÅrsakkobling(Definisjon.AVKLAR_BISTANDSBEHOV, listOf("ANNET")),
            ),
            gjeldendeStegGruppe = StegGruppe.BREV,
            resultat = ResultatKode.INNVILGET,
            årsaker = listOf(Vurderingsbehov.SØKNAD, Vurderingsbehov.G_REGULERING),
            årsakTilOpprettelse = "SØKNAD",
            oppdatertTidspunkt = LocalDateTime.now(clock).minusMinutes(1),
            opprettetAv = "Saksbehandler"
        )
        dataSource.transaction {
            BehandlingRepository(it, clock = clock).opprettBehandling(behandling)
        }

        val uthentet = dataSource.transaction { BehandlingRepository(it).hent(referanse) }

        assertThat(uthentet)
            .usingRecursiveComparison()
            .ignoringCollectionOrder()
            .ignoringFields("id", "hendelser", "snapShotId", "sak.snapShotId")
            .withComparatorForType({ a, b ->
                a.truncatedTo(ChronoUnit.SECONDS).compareTo(b.truncatedTo(ChronoUnit.SECONDS))
            }, LocalDateTime::class.java)
            .isEqualTo(behandling)

        dataSource.transaction {
            BehandlingRepository(it).oppdaterBehandling(
                uthentet!!.copy(
                    venteÅrsak = "XXX"
                )
            )

            BehandlingRepository(it).oppdaterBehandling(
                uthentet.copy(
                    venteÅrsak = "ABC"
                )
            )
        }

        val uthentet2 = dataSource.transaction { BehandlingRepository(it).hent(uthentet!!.referanse) }

        assertThat(uthentet2!!.hendelser).isSortedAccordingTo { c1, c2 ->
            c1.hendelsesTidspunkt.compareTo(
                c2.hendelsesTidspunkt
            )
        }
        assertThat(uthentet2.hendelser.size).isEqualTo(3)
        assertThat(uthentet2.relaterteIdenter).containsExactlyInAnyOrderElementsOf(relaterteIdenter)
    }

    @Test
    fun `lagre to ganger med eksisterende versjon`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, Ident("123456789"))
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
                    oppdatertTidspunkt = LocalDateTime.now(),
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
                    oppdatertTidspunkt = LocalDateTime.now(),
                )
            )
        }

        dataSource.transaction {
            val førsteUthentet = BehandlingRepository(it).hent(referanse)
            assertThat(førsteUthentet).isNotNull()
            val andreUthentet = BehandlingRepository(it).hent(referanse2)
            assertThat(andreUthentet).isNotNull()
            assertThat(førsteUthentet?.versjon).isEqualTo(andreUthentet?.versjon)
        }
    }

    @Test
    fun `lagre oppdatert behandling, henter ut nyeste info`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, Ident("123456789"))
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
                    oppdatertTidspunkt = LocalDateTime.now(),
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
                    sistLøsteAvklaringsbehovTidspunkt = LocalDateTime.now(),
                    oppdatertTidspunkt = LocalDateTime.now(),
                )
            )
        }

        dataSource.transaction {
            assertThat(BehandlingRepository(it).hent(referanse)).isNotNull()
        }
    }

    @Test
    fun `oppdaterer behandling ut av rekkefølge - gjeldende skal ha høyest hendelsestidspunkt`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, Ident("123456789"))
        val sak = opprettTestSak(dataSource, "123456789".tilSaksnummer(), person)

        val referanse = UUID.randomUUID()

        val t0 = LocalDateTime.now().minusDays(3).truncatedTo(ChronoUnit.SECONDS)
        val t1 = LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.SECONDS)
        val t2 = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS)

        val behandlingId = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.OPPRETTET,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = t0,
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    oppdatertTidspunkt = t0,
                )
            )
        }

        // Hendelsen med tidspunkt t2 blir behandlet først (kommer "raskere" gjennom jobb-køen).
        dataSource.transaction {
            BehandlingRepository(it).oppdaterBehandling(
                Behandling(
                    id = behandlingId,
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = t2,
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    oppdatertTidspunkt = t2,
                )
            )
        }

        // Hendelsen med tidspunkt t1 blir behandlet etterpå, selv om t1 < t2 (ute av rekkefølge).
        dataSource.transaction {
            BehandlingRepository(it).oppdaterBehandling(
                Behandling(
                    id = behandlingId,
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.AVSLUTTET,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = t1,
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    oppdatertTidspunkt = t1,
                )
            )
        }

        val gjeldendeStatuser = dataSource.transaction {
            it.queryList(
                "SELECT status, gjeldende FROM behandling_historikk WHERE behandling_id = ? AND gjeldende = TRUE"
            ) {
                setParams { setLong(1, behandlingId.id) }
                setRowMapper { row -> row.getString("status") }
            }
        }

        // Gjeldende rad i databasen skal fortsatt være den med høyest hendelsestidspunkt
        // (t2 -> UTREDES), selv om raden med t1 (AVSLUTTET) ble skrevet sist.
        assertThat(gjeldendeStatuser).containsExactly(BehandlingStatus.UTREDES.name)

        val uthentet = dataSource.transaction { BehandlingRepository(it).hent(referanse) }
        assertThat(uthentet!!.hendelser).hasSize(3)
    }

    @Test
    fun `telle antall fullførte behandlinger`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, Ident("123456789"))
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
                    oppdatertTidspunkt = LocalDateTime.now(),
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
                    oppdatertTidspunkt = LocalDateTime.now(),
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
                    oppdatertTidspunkt = LocalDateTime.now(),
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
                    oppdatertTidspunkt = LocalDateTime.now(),
                )
            )
        }
    }

    @Test
    fun `invalider historikk, og hent ut igjen`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, Ident("123456789"))
        val sak = opprettTestSak(dataSource, "123456789".tilSaksnummer(), person)

        val referanse = UUID.randomUUID()

        val clock = Clock.fixed(Instant.now(), ZoneId.of("Europe/Oslo"))

        val behandlingId = dataSource.transaction {
            BehandlingRepository(it, clock).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.OPPRETTET,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    oppdatertTidspunkt = LocalDateTime.now(clock).minusMinutes(1),
                )
            )
        }

        val littSenereClock = Clock.offset(clock, Duration.ofDays(1))

        dataSource.transaction {
            BehandlingRepository(it, littSenereClock).oppdaterBehandling(
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
                    oppdatertTidspunkt = LocalDateTime.now(clock).minusMinutes(1),
                )
            )
        }

        val uthentet =
            dataSource.transaction { BehandlingRepository(it, littSenereClock).hent(behandlingId) }

        dataSource.transaction {
            BehandlingRepository(it, littSenereClock).invaliderOgLagreNyHistorikk(uthentet)
        }

        val uthentet2 =
            dataSource.transaction { BehandlingRepository(it, littSenereClock).hent(behandlingId) }

        assertThat(uthentet2)
            .usingRecursiveComparison()
            .ignoringFields("hendelser.versjon", "snapShotId", "hendelser.tidspunkt")
            .isEqualTo(uthentet)
    }
}