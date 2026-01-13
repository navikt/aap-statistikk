package no.nav.aap.statistikk.behandling

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
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.sak.tilSaksnummer
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.opprettTestPerson
import no.nav.aap.statistikk.testutils.opprettTestSak
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.*
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
            .ignoringFields("id", "hendelser", "snapShotId", "versjon.id", "sak.snapShotId")
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
            val førsteUthentet = BehandlingRepository(it).hent(referanse)
            assertThat(førsteUthentet).isNotNull()
            val andreUthentet = BehandlingRepository(it).hent(referanse2)
            assertThat(andreUthentet).isNotNull()
            assertThat(førsteUthentet?.versjon).isEqualTo(andreUthentet?.versjon)
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
    }

    @Test
    fun `invalider historikk, og hent ut igjen`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
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